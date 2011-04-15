/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.device;

import java.util.Collection;
import java.util.Map;

/**
 * Interface for device selection criteria.
 */
public interface IDeviceSelectionOptions {

    /**
     * Gets a copy of the serial numbers
     *
     * @return a {@link Collection} of serial numbers
     */
    public Collection<String> getSerials();

    /**
     * Gets a copy of the serial numbers exclusion list
     *
     * @return a {@link Collection} of serial numbers
     */
    public Collection<String> getExcludeSerials();

    /**
     * Gets a copy of the product type list.  These are intended to match {@code ro.product.board}
     * if it's defined for the device, but will fall back to {@code ro.product.device} if
     * {@code board} is undefined.
     *
     * @return a {@link Collection} of product types
     * @see ITestDevice#getProductType
     */
    public Collection<String> getProductTypes();

    /**
     * Gets a copy of the product device type list.  These only match {@code ro.product.device}.
     *
     * @return a {@link Collection} of product device types
     */
    public Collection<String> getProductDeviceTypes();

    /**
     * Returns a map of the property list
     *
     * @return a {@link Map} of device property names to values
     */
    public Map<String, String> getProperties();

    /**
     * @return <code>true</code> if an emulator has been requested
     */
    public boolean emulatorRequested();

    /**
     * @return <code>true</code> if a null device (aka no device required) has been requested
     */
    public boolean nullDeviceRequested();

}
