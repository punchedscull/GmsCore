/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.fitness.internal;

import com.google.android.gms.fitness.result.DataStatsResult;

interface IReadStatsCallback {
    void onResult(in DataStatsResult result) = 1;
}