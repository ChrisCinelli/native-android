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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.res.AssetFileDescriptor;

import com.tealeaf.event.SoundErrorEvent;
import com.tealeaf.event.SoundLoadedEvent;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;

public class SoundManager implements Runnable {
	private ConcurrentHashMap<String, SoundSpec> sounds = new ConcurrentHashMap<String, SoundSpec>();
	private final LinkedBlockingQueue<SoundSpec> loadingQueue = new LinkedBlockingQueue<SoundSpec>();

	private HashSet<SoundSpec> pausedSounds = new HashSet<SoundSpec>();
	private SoundPool soundPool = new SoundPool(15, AudioManager.STREAM_MUSIC, 0);
	private MediaPlayer backgroundMusic = null, loadingSound = null;
	private String backgroundMusicUrl = null;
	private boolean shouldResumeBackgroundMusic = true, shouldResumeLoadingSound = true;
	private ResourceManager resourceManager;
	private TeaLeaf context;

	class SoundSpec {
		public String url;
		public int id;
		public float volume;
		public boolean loop;
		public int stream;
		public boolean loaded;
		public boolean failed;

		public SoundSpec(String url, int id, float volume, boolean loop) {
			this.url = url;
			this.id = id;
			this.volume = volume;
			this.loop = loop;
			this.stream = 0;
			this.loaded = false;
			this.failed = false;
		}
	}

	public SoundManager(TeaLeaf context, ResourceManager resourceManager) {
		this.context = context;
		this.resourceManager = resourceManager;

		new Thread(this).start();
	}

	public void run() {
		while (true) {
			SoundSpec spec;
			try {
				spec = loadingQueue.take();
			} catch (InterruptedException e) {
				continue;
			}
			synchronized (spec) {
				// try loading from the file system
				File sound = resourceManager.getFile(spec.url);
				if (sound == null || !sound.exists()) {
					try {
						// not on the file system, try loading from assets
						AssetFileDescriptor afd = context.getAssets().openFd("resources/" + spec.url);
						spec.id = soundPool.load(afd, 1);
						spec.loaded = true;
						spec.failed = false;
					} catch(IOException e) {
						spec.id = -1;
						spec.failed = true;
						spec.loaded = false;
					}
				} else {
					spec.id = soundPool.load(sound.getAbsolutePath(), 1);
					spec.loaded = true;
					spec.failed = false;
				}
				if (spec.loaded) {
					sendLoadedEvent(spec.url);
				} else {
					sendErrorEvent(spec.url);
				}
				spec.notifyAll();
			}
		}
	}

	public void playSound(String url, float volume, boolean loop) {
		if (url.equals(SoundQueue.LOADING_SOUND)) {
			if (loadingSound != null && shouldResumeLoadingSound) {
				if (!loadingSound.isPlaying()) {
					loadingSound.start();
				}
			} else {
				int id = context.getResources().getIdentifier(SoundQueue.LOADING_SOUND, "raw", context.getPackageName());
				if (id != 0) {
					loadingSound = MediaPlayer.create(context, id);
					if(loadingSound != null) {
						loadingSound.start();
						loadingSound.setLooping(true);
					}
				}
			}
		} else {
			SoundSpec sound = getSound(url);

			if (sound == null) {
				logger.log("{sound} ERROR: Internal sound is null");
			} else {
				int stream = soundPool.play(sound.id, volume, volume, 1, loop ? -1 : 0, 1);
				sound.stream = stream;
				if (pausedSounds.contains(sound)) {
					pausedSounds.remove(sound);
				}
			}
		}
	}

	public void playBackgroundMusic(String url, float volume, boolean loop) {
		shouldResumeBackgroundMusic = true;
		// this means we probably paused it. Just resume, don't start over
		if (url.equals(backgroundMusicUrl) && backgroundMusic != null) {
			backgroundMusic.start();
			backgroundMusic.setVolume(volume, volume);
			backgroundMusic.setLooping(loop);
			return;
		}

		if (backgroundMusic != null) {
			backgroundMusic.stop();
			backgroundMusic.release();
		}
		File file = resourceManager.getFile(url);
		if (file != null && file.exists()) {
			// load it from the fs
			FileInputStream fileInputStream = null;
			backgroundMusic = new MediaPlayer();
			try {
				fileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				// ignore files we can't find
				return;
			}
			try {
				backgroundMusic.setDataSource(fileInputStream.getFD());
			} catch (Exception e) {
				logger.log(e);
			}
		} else {
			// try loading from assets
			AssetFileDescriptor afd = null;
			try {
				afd = context.getAssets().openFd("resources/" + url);
			} catch (IOException e) {
				// ignore files we can't find
				return;
			}
			if(afd != null) {
				try {
					backgroundMusic = new MediaPlayer();
					backgroundMusic.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
				} catch (Exception e) {
					logger.log(e);
					return;
				}
			}
		}

		backgroundMusicUrl = url;
		try {
			backgroundMusic.prepare();
		} catch (Exception e) {
			logger.log(e);
		}

		backgroundMusic.setVolume(volume, volume);
		backgroundMusic.start();
		backgroundMusic.setLooping(loop);
	}

	public void loadBackgroundMusic(String url) {
		if (backgroundMusic != null) {
			backgroundMusic.stop();
			backgroundMusic.release();
		}
		File file = resourceManager.getFile(url);
		if(file != null && file.exists()) {
			// load it from the fs
			FileInputStream fileInputStream = null;
			backgroundMusic = new MediaPlayer();
			try {
				fileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				logger.log(e);
			}
			backgroundMusicUrl = url;
			try {
				backgroundMusic.setDataSource(fileInputStream.getFD());
				backgroundMusic.prepareAsync();
			} catch (Exception e) {
				logger.log(e);
			}
		} else {
			// try loading from assets
			AssetFileDescriptor afd = null;
			try {
				afd = context.getAssets().openFd("resources/" + url);
			} catch (IOException e) {
				logger.log(e);
			}
			if(afd != null) {
				backgroundMusicUrl = url;
				try {
					backgroundMusic = new MediaPlayer();
					backgroundMusic.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
					backgroundMusic.prepareAsync();
				} catch (Exception e) {
					logger.log(e);
				}
			}
		}
	}

	public void stopSound(String url) {
		if (url.equals(backgroundMusicUrl)) {

			if (backgroundMusic != null) {
				backgroundMusic.stop();
				backgroundMusic.release();
				backgroundMusic = null;
			}

			shouldResumeBackgroundMusic = false;
		} else if(url.equals(SoundQueue.LOADING_SOUND)) {
			if(loadingSound != null) {
				loadingSound.stop();
				loadingSound.release();
				loadingSound = null;
				shouldResumeLoadingSound = false;
			}
		} else {
			SoundSpec sound = getSound(url);
			if (sound != null) {
				soundPool.stop(sound.stream);
			}
		}
	}

	public void pauseSound(String url) {
		if (url.equals(backgroundMusicUrl)) {
			if (backgroundMusic != null) {
				backgroundMusic.pause();
				shouldResumeBackgroundMusic = false;
			}
		} else if(url.equals(SoundQueue.LOADING_SOUND)) {
			if(loadingSound != null) {
				loadingSound.pause();
			}
		} else {
			SoundSpec sound = getSound(url);
			if (sound != null) {
				soundPool.pause(sound.stream);
				pausedSounds.add(sound);
			}
		}
	}

	public void setVolume(String url, float volume) {
		if (url.equals(backgroundMusicUrl)) {
			if (backgroundMusic != null) {
				backgroundMusic.setVolume(volume, volume);
			}
		} else {
			SoundSpec sound = getSound(url);
			if (sound != null) {
				sound.volume = volume;
				soundPool.setVolume(sound.stream, volume, volume);
			}
		}
	}

	public void destroy(String url) {
		SoundSpec sound = sounds.get(url);
		sounds.remove(url);

		if (sound != null) {
			soundPool.stop(sound.stream);
			soundPool.unload(sound.id);
		}
	}

	public void onPause() {
		if (backgroundMusic != null) {
			backgroundMusic.pause();
		}
	}

	public void onResume() {
		if (backgroundMusic != null && shouldResumeBackgroundMusic) {
			backgroundMusic.start();
		}
		pausedSounds.clear();
	}

	public SoundSpec loadSound(final String url) {
		return loadSound(url, false);
	}

	public SoundSpec loadSound(final String url, boolean async) {
		SoundSpec spec;
		synchronized (sounds) {
			spec = sounds.get(url);
			if (spec == null) {
				spec = new SoundSpec(url, 0, 0, false);
				sounds.put(url, spec);
				try {
					loadingQueue.put(spec);
				} catch (InterruptedException e) {
					sendErrorEvent(spec.url);
					return null;
				}
			} else if (spec.loaded) {
				sendLoadedEvent(url);
			}
		}

		if (async) { return null; }
		// are we already loading this sound?
		synchronized (spec) {
			// did we try before and have it fail?
			if (spec.failed) { return null; }

			while (!spec.loaded) {
				try {
					spec.wait();
					if (spec.failed) {
						return null;
					}
				} catch (InterruptedException e) {
					return null;
				}
			}
		}
		
		return spec;
	}

	private SoundSpec getSound(String url) {
		SoundSpec sound = null;

		if (url != null) {
			sound = sounds.get(url);
			if (sound == null || !sound.loaded) {
				sound = loadSound(url);
			}
		}

		return sound;
	}

	private void sendLoadedEvent(String url) {
		SoundLoadedEvent event = new SoundLoadedEvent(url);
		EventQueue.pushEvent(event);
	}

	private void sendErrorEvent(String url) {
		SoundErrorEvent event = new SoundErrorEvent(url);
		EventQueue.pushEvent(event);
	}

}
