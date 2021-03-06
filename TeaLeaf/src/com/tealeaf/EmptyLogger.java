/* @license
 * This file is part of the Game Closure SDK.
 *
 * The Game Closure SDK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * The Game Closure SDK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with the Game Closure SDK.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tealeaf;

import android.content.Context;

import com.tealeaf.util.ILogger;

public class EmptyLogger implements ILogger {
	@Override
	public void log(Exception e) {
		// do nothing
	}

	@Override
	public void sendFirstLaunchEvent(Context context) {
		// do nothing
	}

	@Override
	public void sendLaunchEvent(Context context) {
		// do nothing
	}

	@Override
	public void sendErrorEvent(Context context, String payload) {
		// do nothing
	}
	
	@Override
	public void sendGLErrorEvent(Context context, String payload) {
		// do nothing
	}

	@Override
	public void sendDeviceInfoEvent(Context context) {
		// do nothing
	}
}
