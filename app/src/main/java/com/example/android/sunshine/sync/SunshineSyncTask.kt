/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.sync

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import com.example.android.sunshine.data.Forecast
import com.example.android.sunshine.data.SunshinePreferences
import com.example.android.sunshine.utilities.NotificationUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm

object SunshineSyncTask {

    /**
     * Performs the network request for updated weather, parses the JSON from that request, and
     * inserts the new weather information into our ContentProvider. Will notify the user that new
     * weather has been loaded if the user hasn't been notified of the weather within the last day
     * AND they haven't disabled notifications in the preferences screen.
     *
     * @param context Used to access utility methods and the ContentResolver
     */
    @Synchronized
    fun syncWeather(context: Context, compositeDisposable: CompositeDisposable) {

        try {
            val repository = GetWeatherProvider.provideWeatherProvider()
            val func: io.reactivex.Observable<Forecast>
            if (SunshinePreferences.isLocationLatLonAvailable(context)) {
                val preferredCoordinates = SunshinePreferences.getLocationCoordinates(context)
                val latitude = preferredCoordinates[0]
                val longitude = preferredCoordinates[1]
                func = repository.getWeather(latitude, longitude)
            } else {
                val locationQuery = SunshinePreferences.getPreferredWeatherLocation(context)
                func = repository.getWeather(48.14816,17.10674)
            }
            compositeDisposable.add(func
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            { result ->
                                Log.d("TAG", "Downloaded weather")
                                val realm = Realm.getDefaultInstance()
                                realm.executeTransactionAsync {
                                    it.deleteAll()
                                    it.insertOrUpdate(result)
                                }

                                val notificationsEnabled = SunshinePreferences
                                        .areNotificationsEnabled(context)
                                val timeSinceLastNotification = SunshinePreferences
                                        .getEllapsedTimeSinceLastNotification(context)

                                var oneDayPassedSinceLastNotification = false

                                if (timeSinceLastNotification >= DateUtils.DAY_IN_MILLIS) {
                                    oneDayPassedSinceLastNotification = true
                                }
                                if (notificationsEnabled && oneDayPassedSinceLastNotification) {
                                    NotificationUtils.notifyUserOfNewWeather(context,result?.data?.elementAt(0))
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
            )

        } catch (e: Throwable) {
            /* Server probably invalid */
            e.printStackTrace()
        }

    }
}