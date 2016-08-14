/*
 * Copyright 2013-2015 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.location;

import android.app.PendingIntent;
import android.location.Location;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.microg.gms.common.GmsConnector;
import org.microg.gms.common.api.ApiConnection;

public class FusedLocationProviderApiImpl implements FusedLocationProviderApi {
    private static final String TAG = "GmsFusedApiImpl";

    @Override
    public Location getLastLocation(GoogleApiClient client) {
        try {
            Log.d(TAG, "getLastLocation(" + client + ")");
            return LocationClientImpl.get(client).getLastLocation();
        } catch (RemoteException e) {
            Log.w(TAG, e);
            return null;
        }
    }

    @Override
    public PendingResult requestLocationUpdates(GoogleApiClient client,
                                                final LocationRequest request, final LocationListener listener) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.requestLocationUpdates(request, listener);
            }
        });
    }

    @Override
    public PendingResult requestLocationUpdates(GoogleApiClient client,
                                                final LocationRequest request, final LocationListener listener,
                                                final Looper looper) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.requestLocationUpdates(request, listener, looper);
            }
        });
    }

    @Override
    public PendingResult requestLocationUpdates(GoogleApiClient client,
                                                final LocationRequest request, final PendingIntent callbackIntent) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.requestLocationUpdates(request, callbackIntent);
            }
        });
    }

    @Override
    public PendingResult removeLocationUpdates(GoogleApiClient client,
                                               final LocationListener listener) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.removeLocationUpdates(listener);
            }
        });
    }

    @Override
    public PendingResult removeLocationUpdates(GoogleApiClient client,
                                               final PendingIntent callbackIntent) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.removeLocationUpdates(callbackIntent);
            }
        });
    }

    @Override
    public PendingResult setMockMode(GoogleApiClient client, final boolean isMockMode) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.setMockMode(isMockMode);
            }
        });
    }

    @Override
    public PendingResult setMockLocation(GoogleApiClient client, final Location mockLocation) {
        return callVoid(client, new Runnable() {
            @Override
            public void run(LocationClientImpl client) throws RemoteException {
                client.setMockLocation(mockLocation);
            }
        });
    }

    private PendingResult callVoid(GoogleApiClient client, final Runnable runnable) {
        return GmsConnector.call(client, LocationServices.API, new GmsConnector.Callback<LocationClientImpl, Result>() {
            @Override
            public void onClientAvailable(LocationClientImpl client, ResultProvider<Result> resultProvider) throws RemoteException {
                runnable.run(client);
                resultProvider.onResultAvailable(Status.SUCCESS);
            }
        });
    }

    private interface Runnable {
        void run(LocationClientImpl client) throws RemoteException;
    }

}
