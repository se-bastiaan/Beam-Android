/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.se_bastiaan.beam;

/**
 * BeamDevice.java
 * <p/>
 * This class represents the base of a device the application can cast to.
 */
public abstract class BeamDevice {
    protected String id;
    protected String name;
    protected String model;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof BeamDevice) {
            BeamDevice device = (BeamDevice) o;
            return device.id.equals(this.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}