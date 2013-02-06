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

import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.lang.StringBuilder;
import java.io.*;

import com.google.gson.Gson;
import com.tealeaf.event.DialogButtonClickedEvent;
import com.tealeaf.event.OnlineEvent;
import com.tealeaf.plugin.PluginManager;
import com.tealeaf.util.Connection;
import com.tealeaf.util.ILogger;
import com.tealeaf.util.XMLHttpRequest;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.graphics.Bitmap;
import android.os.RemoteException;


import android.util.DisplayMetrics;
import android.view.WindowManager;

public class NativeShim {
	private SoundQueue soundQueue;
	private TextureLoader textureLoader;
	private TextManager textManager;
	private LocalStorage localStorage;
	private ContactList contactList;
	private Haptics haptics;
	private LocationManager locationManager;
	private ServiceWrapper service;
	private TeaLeaf context;
	private ResourceManager resourceManager;
	private ArrayList<TeaLeafSocket> sockets = new ArrayList<TeaLeafSocket>();
	private ArrayList<String> overlayEvents = new ArrayList<String>();
	private ILogger remoteLogger;
	private ConnectivityManager connectivityManager;
	private NetworkStateReceiver networkStateReceiver;
	private boolean onlineStatus;
	private Gson gson = new Gson();
	public NativeShim(TextManager textManager, TextureLoader textureLoader, SoundQueue soundQueue,
			LocalStorage localStorage, ContactList contactList,
			LocationManager locationManager, ServiceWrapper service, ResourceManager resourceManager,
			TeaLeaf context) {
		this.textManager = textManager;
		this.textureLoader = textureLoader;
		new Thread(this.textureLoader).start();
		this.soundQueue = soundQueue;
		this.localStorage = localStorage;
		this.contactList = contactList;
		this.haptics = new Haptics(context);
		this.locationManager = locationManager;
		this.service = service;
		this.resourceManager = resourceManager;
		this.context = context;
		this.remoteLogger = context.getRemoteLogger();
		this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		this.networkStateReceiver = new NetworkStateReceiver(this);
		this.onlineStatus = false;
		this.updateOnlineStatus();

		IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
		context.registerReceiver(this.networkStateReceiver, filter);

	}

	public void onPause() {
		service.unbind();
	}
	public void onResume() {
		service.rebind();
	}

	public String getVersionCode() {
		return context.getOptions().getBuildIdentifier();
	}

	//xhr
	public void sendXHR(int id, String method, String url, String data, boolean async, String[] requestHeaders) {
		HashMap<String,String> requestHeadersMap = new HashMap<String,String>();
		if(requestHeaders != null) {
			for(int i = 0; i < requestHeaders.length / 2; i++) {
				requestHeadersMap.put(requestHeaders[i*2], requestHeaders[i*2+1]);
			}
		}
		XMLHttpRequest xhr = new XMLHttpRequest(id, method, url, data, async ,requestHeadersMap);

		if (async) {
			Thread xhrThread = new Thread(xhr);
			xhrThread.setPriority(Thread.MIN_PRIORITY+2);
			xhrThread.start();
		} else {
			xhr.run();
		}
	}

	// Display
	public DisplayMetrics getDisplayMetrics() {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(metrics);
		return metrics;
	}

	//Haptics
	public void cancel() {
		haptics.cancel();
	}
	public void vibrate(long milliseconds) {
		haptics.vibrate(milliseconds);
	}
	public void vibrate(long[] pattern, int repeat) {
		haptics.vibrate(pattern, repeat);
	}
	public boolean hasVibrator() {
		return haptics.hasVibrator();
	}

	public void showDialog(final String title, final String text, final String imageUrl, final String[] buttons, int[] callbacks) {
		final Runnable[] cbs = new Runnable[callbacks.length];
		for(int i = 0; i < callbacks.length; i++) {
			final int callback = i;
			cbs[i] = new Runnable() {
				public void run() {
					EventQueue.pushEvent(new DialogButtonClickedEvent(callback));
				}
			};
		}
		Bitmap bitmap = null;
		if(imageUrl != null) {
			textureLoader.getImage(imageUrl);
		}
		final Bitmap image = bitmap;
		context.runOnUiThread(new Runnable() {
			public void run() {
				JSDialog.showDialog(context, image, title, text, buttons, cbs);
			}
		});
	}

	//Purchase
	public void buy(String id) {
		service.buy(id);
	}
	public void restore() {
		try {
			service.get().restoreResults();
		} catch (RemoteException e) {
			logger.log(e);
		}
	}
	public void confirmPurchase(String notifyId) {
		Intent i = new Intent("com.tealeaf.CONFIRM_PURCHASE");
		i.setClass(context, TeaLeafService.class);
		i.putExtra("NotifyId", notifyId);
		context.startService(i);
	}
	public boolean marketAvailable() {
		return service.get().getMarketSupported();
	}

	//Overlay
	public void loadOverlay(final String url) {
		context.runOnUiThread(new Runnable() { public void run() { context.getOverlay().load(url); } });
	}
	public void showOverlay() {
		context.runOnUiThread(new Runnable() { public void run() { context.getOverlay().show(); } });
	}
	public void hideOverlay() {
		context.runOnUiThread(new Runnable() { public void run() { context.getOverlay().hide(); } });
	}
	public void sendEventToOverlay(final String event) {
		context.runOnUiThread(new Runnable() { public void run() { context.getOverlay().sendEvent(event); } });
	}

	private int textInputId = 0;
	public int showInputPrompt(final String title, final String message, final String value, final boolean autoShowKeyboard) {
		return InputPrompt.getInstance().showInputPrompt(context, title, message, value, autoShowKeyboard);
	}
	//TextInputView
	public int createTextBox() {
		return context.getTextInputView().createNew();
	}
	public int createTextBox(int x, int y, int w, int h, String initialValue) {
		return context.getTextInputView().createNew(x, y, w, h, initialValue);
	}
	public void destroyTextBox(int id) {
		context.getTextInputView().destroy(id);
	}

	public void showTextBox(int id) {
		context.getTextInputView().show(id);
	}
	public void hideTextBox(int id) {
		context.getTextInputView().hide(id);
	}
	public void textBoxSelectAll(int id) {
		context.getTextInputView().selectAll(id);
	}

	public boolean getTextBoxVisible(int id) {
		return context.getTextInputView().getVisible(id);
	}
	public int getTextBoxX(int id) {
		return context.getTextInputView().getX(id);
	}
	public int getTextBoxY(int id) {
		return context.getTextInputView().getY(id);
	}
	public int getTextBoxWidth(int id) {
		return context.getTextInputView().getWidth(id);
	}
	public int getTextBoxHeight(int id) {
		return context.getTextInputView().getHeight(id);
	}
	public int getTextBoxType(int id) {
		return context.getTextInputView().getType(id);
	}
	public String getTextBoxValue(int id) {
		return context.getTextInputView().getValue(id);
	}
	public float getTextBoxOpacity(int id) {
		return context.getTextInputView().getOpacity(id);
	}

	public void setTextBoxVisible(int id, boolean visible) {
		context.getTextInputView().setVisible(id, visible);
	}
	public void setTextBoxPosition(int id, int x, int y, int w, int h) {
		context.getTextInputView().setPosition(id, x, y, w, h);
	}
	public void setTextBoxDimensions(int id, int w, int h) {
		context.getTextInputView().setDimensions(id, w, h);
	}
	public void setTextBoxX(int id, int x) {
		context.getTextInputView().setX(id, x);
	}
	public void setTextBoxY(int id, int y) {
		context.getTextInputView().setY(id, y);
	}
	public void setTextBoxWidth(int id, int w) {
		context.getTextInputView().setWidth(id, w);
	}
	public void setTextBoxHeight(int id, int h) {
		context.getTextInputView().setHeight(id, h);
	}
	public void setTextBoxType(int id, int type) {
		context.getTextInputView().setType(id, type);
	}
	public void setTextBoxValue(int id, String value) {
		context.getTextInputView().setValue(id, value);
	}
	public void setTextBoxOpacity(int id, float value) {
		context.getTextInputView().setOpacity(id, value);
	}

	//Locale
	public String getCountry() {
		return Locale.getDefault().getCountry();
	}

	public String getLanguage() {
		return Locale.getDefault().getLanguage();
	}

	//Device
	public String getDeviceID() {
		return Device.getDeviceID(context, context.getSettings());
	}
	public String getDeviceInfo() {
		return Device.getDeviceInfo();
	}

	public void reload() {
		context.reload();
	}

	//Textures
	public int measureText(String font, int size, String text) {
		int textSize = textManager.measureText(font, size, text);
		return textSize;
	}
	public void loadTexture(String url) {
		textureLoader.loadTexture(url);
	}
	public int getNextCameraId() {
		return textureLoader.getNextCameraId();
	}
	public int getNextGalleryId() {
		return textureLoader.getNextGalleryId();
	}
	public void clearTextureData() {
		clearTextures();
	}
	public void setHalfsizedTexturesSetting(boolean on) {
		Settings settings = context.getSettings();
		settings.setBoolean("@__use_halfsized_textures__", on);
	}

	//Sound
	private static final boolean DO_SOUND = true;
	public void loadSound(final String url) {
		if (!DO_SOUND) { return; }
		soundQueue.loadSound(url);
	}
	public void playSound(String url, float volume, boolean loop) {
		if (!DO_SOUND) { return;}
		soundQueue.playSound(url, volume, loop);
	}
	public void loadBackgroundMusic(String url) {
		if (!DO_SOUND) { return;}
		soundQueue.loadBackgroundMusic(url);
	}
	public void playBackgroundMusic(final String url, final float volume, final boolean loop) {
		if (!DO_SOUND) { return;}
		soundQueue.playBackgroundMusic(url, volume, loop);
	}
	public void stopSound(String url) {
		soundQueue.stopSound(url);
	}
	public void pauseSound(String url) {
		soundQueue.pauseSound(url);
	}
	public void setVolume(String url, float volume) {
		soundQueue.setVolume(url, volume);
	}

	// Sockets
	public void sendData(int id, String data) {
		if(sockets.size() > id) {
			TeaLeafSocket socket = sockets.get(id);
			if (socket != null) {
				socket.write(data);
			} else {
				logger.log("{socket} WARNING: Send data failed on broken socket");
			}
		}
	}
	public int openSocket(String host, int port) {
		int id = sockets.size();
		logger.log("{socket} Connecting to ", host, ":", port, " (id=", id, ")");

		TeaLeafSocket socket = new TeaLeafSocket(host, port, id);
		sockets.add(socket);
		new Thread(socket).start();
		return id;
	}
	public void closeSocket(int id) {
		TeaLeafSocket socket = sockets.get(id);
		if (socket != null) {
			socket.close();
		}
	}

	//Source Loading
	public String loadSourceFile(String url) {
		TeaLeafOptions options = context.getOptions();
		String sourceString = null;
		if (options.isDevelop() && options.get("forceURL", false)) {
			// load native.js.mp3 from the file system
			// read file in
			String path = resourceManager.getStorageDirectory() + "/build/debug/native-android/";
			String result = null;
			DataInputStream in = null;
			try {
				File f = new File(path + url);
				byte[] buffer = new byte[(int) f.length()];
				in = new DataInputStream(new FileInputStream(f));
				in.readFully(buffer);
				result = new String(buffer);
			} catch (FileNotFoundException e) {
				logger.log("Error loading", url, "from", path);
				logger.log("File not found!");
				throw new RuntimeException("File not found in loadSourceFile");
			} catch (IOException e) {
				throw new RuntimeException("IO problem in fileToString", e);
			} finally {
				try {
					if (in != null) {
						in.close();
					}
				} catch (IOException e) {
					logger.log(e);
				}
			}
			sourceString = result;

		} else {
			sourceString = resourceManager.getFileContents(url);
		}
		return sourceString;
	}

	//GL stuff
	public native static void initGL(int glName);
	public native static void setSingleShader(boolean on);
	public native static void setHalfsizedTextures(boolean on);
	//Contacts stuff
	public static native void dispatchContactCallback(int cb, long id, String name);


	//LocalStorage
	public void setData(String key, String data) {
		localStorage.setData(key, data);
	}
	public String getData(String key) {
		return localStorage.getData(key);
	}
	public void removeData(String key) {
		localStorage.removeData(key);
	}
	public void clearData() {
		localStorage.clear();

	}

	//location manager
	public void setLocation(String uri) {
		locationManager.setLocation(uri);
	}

	// plugins
	public void pluginsCall(String className, String methodName, Object[] params) {
		PluginManager.call(className, methodName, params);
	}

	// native
	public String getMarketUrl() {
		return "market://details?id=" + context.getPackageName();
	}
	public void startGame(String appid) {
		logger.log("{tealeaf} Starting", appid == null ? "the default game" : appid);
		context.glView.startCrossPromo(appid);
	}
	public void applyUpdate() {
		logger.log("{tealeaf} Applying game update");
		TeaLeafOptions options = context.getOptions();
		Settings settings = context.getSettings();
		if(settings.isMarketUpdate(options.getBuildIdentifier())) {
			locationManager.setLocation(getMarketUrl());
		} else {
			Updater updater = new Updater(context, context.getOptions(), settings.getUpdateUrl(options.getBuildIdentifier()));
			if(updater.apply()) {
				settings.clear("updating_now");
				options.setBuildIdentifier(settings.getUpdateBuild(options.getBuildIdentifier()));
				settings.markUnpacked(options.getBuildIdentifier());
				startGame(null);
			}
		}
	}
	public boolean sendActivityToBack() {
		boolean success = context.moveTaskToBack(true);

		if (!success) {
			logger.log("{tealeaf} WARNING: Unable to move activity to background");
		} else {
			logger.log("{tealeaf} Moved activity to background");
		}

		return success;
	}


	public String getStorageDirectory() {
		return resourceManager.getStorageDirectory();
		//return resourceManager.resolveFile(filename);
	}

	// network status
	// TODO move all of this to TeaLeaf and register the receiver there, and make it use that directly instead
	public void updateOnlineStatus(){
		this.onlineStatus = Connection.available(context);

		if (this.onlineStatus) {
			logger.log("{reachability} Online");
		} else {
			logger.log("{reachability} Offline");
		}
	}
	public boolean getOnlineStatus() {
		return this.onlineStatus;
	}
	public void sendOnlineEvent(){
		EventQueue.pushEvent(new OnlineEvent(this.onlineStatus));
	}

	public void uploadDeviceInfo() {
		remoteLogger.sendDeviceInfoEvent(context);
	}
	public void logJavascriptError(final String message, final String url, final int lineNumber) {
		if (context.getOptions().isDevelop()) {
			context.runOnUiThread(new Runnable() {
				public void run() {
					JSDialog.showDialog(context, null, "JS Error", url + " line " + lineNumber + "\n" + message,
							new String[] {"OK"},
							new Runnable[] {new Runnable() { public void run() {} }
					});
				}
			});
		} else {
			remoteLogger.sendErrorEvent(context, "JS Error at " + url + " line " + lineNumber + ": " + message);
		}
	}

	public int[] reportGlError(int errorCode) {
		Settings settings = context.getSettings();
		String glErrorStr = settings.getString("gl_errors", "NONE");
		String errorCodeStr = Integer.toString(errorCode);


		ArrayList<String> glErrorList = null;
		if (glErrorStr.equals("NONE")) {
			glErrorList = new ArrayList<String>();
		} else {
			glErrorList = new ArrayList<String>(Arrays.asList(glErrorStr.split(",")));
		}

		int[] glErrorInts = new int[glErrorList.size()];

		if (glErrorList.contains(errorCodeStr)) {
			//create the return array, do not log as this error has been seen
			for (int i = 0; i < glErrorInts.length; i++) {
				glErrorInts[i] = Integer.parseInt(glErrorList.get(i));
			}

		} else {
			//create the return array and log
			glErrorList.add(errorCodeStr);

			//build the settings string
			StringBuilder stringBuilder = new StringBuilder();
			for (int i = 0; i < glErrorList.size(); i++) {
				stringBuilder.append(glErrorList.get(i));
				if (i != glErrorList.size() - 1) {
					stringBuilder.append(",");
				}
			}

			//save the new errors
			settings.setString("gl_errors", stringBuilder.toString());

			//log the error
			String errorString = "GL ERROR: " + errorCodeStr ;
			if (context.getOptions().isDevelop()) {
				logger.log(errorString);
			} else {
				remoteLogger.sendGLErrorEvent(context, errorString);
			}

			//create the return array
			for (int i = 0; i < glErrorInts.length; i++) {
				glErrorInts[i] = Integer.parseInt(glErrorList.get(i));
			}
		}

		return glErrorInts;
	}

	public void takeScreenshot() {
		context.takeScreenshot = true;
	}

	public int getTotalMemory() {
		return Device.getTotalMemory();
	}

	public void logNativeError() {
		Intent intent = new Intent(context, CrashRecover.class);
		context.startActivity(intent);
	}

	//build info
	public String getSDKHash() {
		return context.getOptions().getSDKHash();
	}

	public String getAndroidHash() {
		return context.getOptions().getAndroidHash();
	}

	public String getGameHash() {
		return context.getOptions().getGameHash();
	}

	public String getAndroidVersion() {
		return android.os.Build.VERSION.RELEASE;
	}

	public String getSimulateID() {
		return context.getOptions().getSimulateID();
	}

	//Install stuff
	public String getInstallReferrer() {
		return context.getSettings().getString("referrer", "");
	}

	//Local
	public String getLocaleCountry() {
		return LocaleInfo.getCountry(context);
	}

	public String getLocaleLanguage() {
		return LocaleInfo.getLanguage(context);
	}

	//Initialization and Running JS
	public static native boolean initIsolate();
	public static native void init(Object shim, String codeHost, String tcpHost, int codePort, int tcpPort, String entryPoint, String sourceDir, int width, int height, boolean remote_loading, String simulateID);
	public static native boolean initJS(String uri, String androidHash);
	public static native void destroy();
	public static native void reset();
	public static native void run();

	//GL stuff
	public static native void step(int dt);
	public static native void resizeScreen(int w, int h);
	public static native void reloadTextures();
	public static native void reloadCanvases();
	public static native void clearTextures();
	public static native void onTextureLoaded(String url, int name, int width, int height, int originalWidth, int originalHeight, int numChannels);
	public static native void onTextureFailedToLoad(String url);

	//Input stuff
	public static native void dispatchEvents(String[] event);
	public static native void dispatchInputEvents(int[] ids, int[] types, int[] xs, int[] ys, int count);

	public static native void saveTextures();
}