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

package com.github.se_bastiaan.beam.airplay;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.github.se_bastiaan.beam.logger.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

/**
 * AirPlayDiscovery.java
 *
 * mDNS discovery of AirPlay devices. Gives callback when devices are found/removed/resolved.
 */
public class AirPlayDiscovery implements ServiceListener {

    private final String TAG = getClass().getCanonicalName();
    private static final String SERVICE_TYPE = "_airplay._tcp.local.";
    private static final String MULTICAST_LOCK_NAME = "AIRPLAY_MULTICAST";

    private static AirPlayDiscovery INSTANCE;

    private JmDNS jmDns;
    private Thread dnsThread;
    private WifiManager.MulticastLock lock;
    private ServiceListener serviceListener;
    private InetAddress deviceAddress;
    private Handler handler;

    private AirPlayDiscovery(Context context, ServiceListener serviceListener) {
        this.serviceListener = serviceListener;

        handler = new Handler(Looper.getMainLooper());

        WifiManager wifi = (WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock(MULTICAST_LOCK_NAME);
        lock.setReferenceCounted(true);
        lock.acquire();

        dnsThread = new Thread() {
            @Override
            public void run() {
                try {
                    deviceAddress = getWifiInetAddress();
                    if (deviceAddress == null) {
                        Logger.d(TAG, "Unable to get local ip address");
                        return;
                    }

                    jmDns = JmDNS.create(deviceAddress);
                    jmDns.addServiceListener(SERVICE_TYPE, AirPlayDiscovery.this);
                    Logger.d(TAG, "Using local address " + deviceAddress.getHostAddress());
                } catch (Exception e) {
                    Logger.d(TAG, "Error: " + e.getMessage() == null ? "Unable to initialize discovery service" : e.getMessage());
                }
            }
        };
    }

    /**
     * Get existing instance of AirplayDiscovery or create new
     *
     * @param context Context
     * @param serviceListener Listener
     * @return AirPlayDiscovery
     */
    public static AirPlayDiscovery getInstance(Context context, ServiceListener serviceListener) {
        if (INSTANCE == null) {
            INSTANCE = new AirPlayDiscovery(context, serviceListener);
        }
        INSTANCE.serviceListener = serviceListener;
        return INSTANCE;
    }

    /**
     * Start the jmDNS service and try to find AirPlay devices on the network
     */
    public void start() {
        lock.acquire();
        if (!dnsThread.isAlive()) dnsThread.start();
    }

    /**
     * Stop the jmDNS service
     */
    public void stop() {
        if (jmDns != null) {
            try {
                jmDns.removeServiceListener(SERVICE_TYPE, this);
                jmDns.close();
            } catch (Exception e) {
                Logger.w(TAG, "Error while stopping the jmDNS service: " + e.getMessage(), e);
            }
        }

        if (lock != null) {
            lock.release();
        }
    }

    @Override
    public void serviceAdded(final ServiceEvent event) {
        serviceListener.serviceAdded(event);
        handler.post(new Runnable() {
            @Override
            public void run() {
                jmDns.requestServiceInfo(event.getType(), event.getName(), 30000);
            }
        });
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        serviceListener.serviceRemoved(event);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        serviceListener.serviceResolved(event);
    }

    /**
     * Get the ip address used by the wifi interface
     *
     * @return IP4v InetAddress
     */
    private InetAddress getWifiInetAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return (inetAddress);
                    }
                }
            }
        } catch (Exception e) {
            return (null);
        }
        return (null);
    }
}
