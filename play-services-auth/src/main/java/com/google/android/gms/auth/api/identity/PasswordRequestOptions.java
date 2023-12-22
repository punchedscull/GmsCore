/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.auth.api.identity;

import org.microg.safeparcel.AutoSafeParcelable;

public class PasswordRequestOptions extends AutoSafeParcelable {
    @Field(1)
    public boolean primary;

    public static final Creator<PasswordRequestOptions> CREATOR = findCreator(PasswordRequestOptions.class);
}
