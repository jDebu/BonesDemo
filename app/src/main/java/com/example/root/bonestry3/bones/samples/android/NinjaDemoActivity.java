package com.example.root.bonestry3.bones.samples.android;

import com.example.root.bonestry3.glfont.GLFont;

import java.util.LinkedList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import bones.samples.android.R;
import raft.jpct.bones.Animated3D;
import raft.jpct.bones.AnimatedGroup;
import raft.jpct.bones.BonesIO;
import raft.jpct.bones.SkeletonPose;
import raft.jpct.bones.SkinClip;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;

import com.threed.jpct.Animation;
import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Interact2D;
import com.threed.jpct.Light;
import com.threed.jpct.Logger;
import com.threed.jpct.Mesh;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;


public class NinjaDemoActivity extends Activity {
	
	/** set this to true to allow mesh keyframe animation */
	private static final boolean MESH_ANIM_ALLOWED = false;
	
	private static final int MENU_STOP_ANIM = 1;
	private static final int MENU_USE_MESH_ANIM = 2;
	
	private static final int GRANULARITY = 25;
	
	/** ninja placement locations. values are in angles */
	private static final float[] LOCATIONS = new float[] {0, 180, 90, 270, 45, 225, 315, 135};  
	
	private static final Rect[] BUTTON_BOUNDS = new Rect[2];
	
	private GLSurfaceView mGLView;
	private final MyRenderer renderer = new MyRenderer();
	private World world = null;
	
	private CameraOrbitController cameraController;
	
	private PowerManager.WakeLock wakeLock;
	
	private long frameTime = System.currentTimeMillis();
	private long aggregatedTime = 0;
	private float animateSeconds  = 0f;
	private float speed = 1f;
	
	private int animation = -1;
	private boolean useMeshAnim = false;
	
	private AnimatedGroup masterNinja;
	private final List<AnimatedGroup> ninjas = new LinkedList<AnimatedGroup>();
	
	private long lastTouchTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.log("onCreate");
		
		super.onCreate(savedInstanceState);
		
		mGLView = new GLSurfaceView(getApplication());
		setContentView(mGLView);

		mGLView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
			public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
				// Ensure that we get a 16bit framebuffer. Otherwise, we'll fall
				// back to Pixelflinger on some device (read: Samsung I7500)
				int[] attributes = new int[] { EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE };
				EGLConfig[] configs = new EGLConfig[1];
				int[] result = new int[1];
				egl.eglChooseConfig(display, attributes, configs, 1, result);
				return configs[0];
			}
		});
		
		mGLView.setRenderer(renderer);
		
		if (world != null)
			return;
		
		world = new World();
		
		try {
			Resources res = getResources();
			masterNinja = BonesIO.loadGroup(res.openRawResource(R.raw.ninja_group));//R.raw.ninja);
			if (MESH_ANIM_ALLOWED)
				createMeshKeyFrames();
			addNinja();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
				
		world.setAmbientLight(127, 127, 127);
		world.buildAllObjects();

		float[] bb = renderer.calcBoundingBox();
		float height = (bb[3] - bb[2]); // ninja height
		new Light(world).setPosition(new SimpleVector(0, -height/2, height));
		
		cameraController = new CameraOrbitController(world.getCamera());
		cameraController.cameraAngle = 0;
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Bones-Demo");
	}

	private void createMeshKeyFrames() {
		Config.maxAnimationSubSequences = masterNinja.getSkinClipSequence().getSize() + 1; // +1 for whole sequence
		
		int keyframeCount = 0;
		final float deltaTime = 0.2f; // max time between frames
		
		for (SkinClip clip : masterNinja.getSkinClipSequence()) {
			float clipTime = clip.getTime();
			int frames = (int) Math.ceil(clipTime / deltaTime) + 1;
			keyframeCount += frames;
		}
		
		Animation[] animations = new Animation[masterNinja.getSize()];
		for (int i = 0; i < masterNinja.getSize(); i++) {
			animations[i] = new Animation(keyframeCount);
			animations[i].setClampingMode(Animation.USE_CLAMPING);
		}
		//System.out.println("------------ keyframeCount: " + keyframeCount + ", mesh size: " + masterNinja.getSize());
		int count = 0;
		
		int sequence = 0;
		for (SkinClip clip : masterNinja.getSkinClipSequence()) {
			float clipTime = clip.getTime();
			int frames = (int) Math.ceil(clipTime / deltaTime) + 1;
			float dIndex = 1f / (frames - 1);
			
			for (int i = 0; i < masterNinja.getSize(); i++) {
				animations[i].createSubSequence(clip.getName());
			}
			//System.out.println(sequence + ": " + clip.getName() + ", frames: " + frames);
			for (int i = 0; i < frames; i++) {
				masterNinja.animateSkin(dIndex * i, sequence + 1);
				
				for (int j = 0; j < masterNinja.getSize(); j++) {
					Mesh keyframe = masterNinja.get(j).getMesh().cloneMesh(true);
					keyframe.strip();
					animations[j].addKeyFrame(keyframe);
					count++;
					//System.out.println("added " + (i + 1) + " of " + sequence + " to " + j + " total: " + count);
				}
			}
			sequence++;
		}
		for (int i = 0; i < masterNinja.getSize(); i++) {
			masterNinja.get(i).setAnimationSequence(animations[i]);
		}
		masterNinja.get(0).getSkeletonPose().setToBindPose();
		masterNinja.get(0).getSkeletonPose().updateTransforms();
		masterNinja.applySkeletonPose();
		masterNinja.applyAnimation();
		
		Logger.log("created mesh keyframes, " + keyframeCount + "x" + masterNinja.getSize());
	}

	@Override
	protected void onPause() {
		Logger.log("onPause");
		super.onPause();
		mGLView.onPause();
		
		if (wakeLock.isHeld())
			wakeLock.release();
	}

	@Override
	protected void onResume() {
		Logger.log("onResume");
		super.onResume();
		mGLView.onResume();
		
		frameTime = System.currentTimeMillis();
		aggregatedTime = 0;
		
		if (!wakeLock.isHeld())
			wakeLock.acquire();
	}

	@Override
	protected void onStop() {
		Logger.log("onStop");
		super.onStop();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		long now = System.currentTimeMillis();
		
		if ((BUTTON_BOUNDS[0]  != null) && (now - lastTouchTime > 100)) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_UP:
					int x = (int)event.getX();
					int y = (int)event.getY();
					
//					System.out.println("x: " + x + ", y: " + y);
//					System.out.println(BUTTON_BOUNDS[0] + " " + BUTTON_BOUNDS[0].contains(x, y));
//					System.out.println(BUTTON_BOUNDS[1] + " " + BUTTON_BOUNDS[1].contains(x, y));
					
					if (BUTTON_BOUNDS[0].contains(x, y)) {
						mGLView.queueEvent(new Runnable() {
							@Override
							public void run() {
								addNinja();
							}
						});
						lastTouchTime = now;
						return true;
					} else if (BUTTON_BOUNDS[1].contains(x, y)) {
						mGLView.queueEvent(new Runnable() {
							@Override
							public void run() {
								removeNinja();
							}
						});
						lastTouchTime = now;
						return true;
					}  
			}
		}
		if (cameraController.onTouchEvent(event))
			return true;
		
		return super.onTouchEvent(event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (cameraController.onKeyUp(keyCode, event))
			return true;
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_0:
	        case KeyEvent.KEYCODE_1:
	        case KeyEvent.KEYCODE_2:
	        case KeyEvent.KEYCODE_3:
	        case KeyEvent.KEYCODE_4:
	        case KeyEvent.KEYCODE_5:
	        case KeyEvent.KEYCODE_6:
	        case KeyEvent.KEYCODE_7:
	        case KeyEvent.KEYCODE_8:
	        case KeyEvent.KEYCODE_9:
	        	animation = keyCode - KeyEvent.KEYCODE_0;
	        	return true;
	        case KeyEvent.KEYCODE_Q:
	        	speed /= 1.1;
	        	return true;
	        case KeyEvent.KEYCODE_W:
	        	if (speed == 0) {
	        		speed = 0.1f;
	        		return true;
	        	}
	        	speed *= 1.1;
	        	return true;
		}
		if (cameraController.onKeyDown(keyCode, event))
			return true;
		return super.onKeyDown(keyCode, event);
	}	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    menu.add(0, MENU_STOP_ANIM, 0, "Stop Animation");
	    if (MESH_ANIM_ALLOWED)
	    	menu.add(0, MENU_USE_MESH_ANIM, 0, "Use Mesh Animation").setCheckable(true);
	    
	    SubMenu animMenu = menu.addSubMenu("Animation");
		int menuItem = 101;
		for (SkinClip clip : masterNinja.getSkinClipSequence()) {
			animMenu.add(0, menuItem++, 1, "Anim: " + clip.getName());
		}
	    
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
		    case MENU_STOP_ANIM:
		        animation = 0; 
		        return true;
		    case MENU_USE_MESH_ANIM:
		    	useMeshAnim = !useMeshAnim;
		    	item.setTitle(useMeshAnim ? "Use Skin Animation" : "Use Mesh Animation");
		        return true;
	    }
	    if (item.getItemId() > 100) {
	        animation = item.getItemId() - 100;
	        return true;
	    }
	    return false;
	}	
	
	private void addNinja() {
		if (ninjas.size() == LOCATIONS.length)
			return;
		
		AnimatedGroup ninja = masterNinja.clone(AnimatedGroup.MESH_DONT_REUSE);
		float[] bb = renderer.calcBoundingBox();
		float radius = (bb[3] - bb[2]) * 0.5f; // half of height
		double angle = Math.toRadians(LOCATIONS[ninjas.size()]);

		ninja.setSkeletonPose(new SkeletonPose(ninja.get(0).getSkeleton()));
		ninja.getRoot().translate((float)(Math.cos(angle) * radius), 0, (float)(Math.sin(angle) * radius));
		
		ninja.addToWorld(world);
		ninjas.add(ninja);
		Logger.log("added new ninja: " + ninjas.size());
	}
	
	private void removeNinja() {
		if (ninjas.size() == 1)
			return;
		
		AnimatedGroup ninja = ninjas.remove(ninjas.size()-1);
		ninja.removeFromWorld(world);
		Logger.log("removed ninja: " + (ninjas.size() + 1));
	}
	
	class MyRenderer implements GLSurfaceView.Renderer {

		private FrameBuffer frameBuffer = null;

		private int fps = 0;
		private int lfps = 0;

		private long fpsTime = System.currentTimeMillis();

		private GLFont glFont;
		private GLFont buttonFont;
		
		public MyRenderer() {
			Config.maxPolysVisible = 5000;
			Config.farPlane = 1500;
		}
		
		@Override
		public void onSurfaceChanged(GL10 gl, int w, int h) {
			Logger.log("onSurfaceChanged");
			if (frameBuffer != null) {
				frameBuffer.dispose();
			}
			frameBuffer = new FrameBuffer(gl, w, h);
			
			BUTTON_BOUNDS[0] = new Rect(0, 0, 100, 100); 
			BUTTON_BOUNDS[1] = new Rect(w-100, 0, w, 100); 
			
			autoAdjustCamera();
			cameraController.placeCamera();
		}

		@Override
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			Logger.log("onSurfaceCreated");

			TextureManager.getInstance().flush();
			Resources res = getResources();

			Texture texture = new Texture(res.openRawResource(R.raw.ninja_texture));
			texture.keepPixelData(true);
			TextureManager.getInstance().addTexture("ninja", texture);
			
			for (Animated3D a : masterNinja) 
				a.setTexture("ninja");
			
			for (AnimatedGroup group : ninjas) {
				for (Animated3D a : group) 
					a.setTexture("ninja");
			}
			
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setTypeface(Typeface.create((String)null, Typeface.BOLD));
			
			paint.setTextSize(16);
			glFont = new GLFont(paint);
			
			paint.setTextSize(50);
			buttonFont = new GLFont(paint, "+-");
		}

		@Override
		public void onDrawFrame(GL10 gl) {
			if (frameBuffer == null)
				return;

			
			long now = System.currentTimeMillis();
			aggregatedTime += (now - frameTime); 
			frameTime = now;
			
			if (aggregatedTime > 1000) {
				aggregatedTime = 0;
			}
			

			while (aggregatedTime > GRANULARITY) {
				aggregatedTime -= GRANULARITY;
				animateSeconds += GRANULARITY * 0.001f * speed;
				cameraController.placeCamera();
			}
			
			if (animation > 0 && masterNinja.getSkinClipSequence().getSize() >= animation) {
				float clipTime = masterNinja.getSkinClipSequence().getClip(animation-1).getTime();
				if (animateSeconds > clipTime) {
					animateSeconds = 0;
				}
				float index = animateSeconds / clipTime;
				if (useMeshAnim) {
					for (AnimatedGroup group : ninjas) {
						for (Animated3D a : group) 
							a.animate(index, animation);
					}
				} else {
					for (AnimatedGroup group : ninjas) {
						group.animateSkin(index, animation);
//							if (!group.isAutoApplyAnimation())
//								group.applyAnimation();
					}
				}
				
			} else {
				animateSeconds = 0f;
			}
			
			frameBuffer.clear();
			world.renderScene(frameBuffer);
			world.draw(frameBuffer);
			
			buttonFont.blitString(frameBuffer, "+", 10, 40, 10, RGBColor.WHITE);
			buttonFont.blitString(frameBuffer, "-", frameBuffer.getWidth()-30, 40, 10, RGBColor.WHITE);
			
			glFont.blitString(frameBuffer, lfps + "/" + ninjas.size() + " " + (useMeshAnim ? "M" : "S"), 
					5, frameBuffer.getHeight()-5, 10, RGBColor.WHITE);
			
			frameBuffer.display();

			if (System.currentTimeMillis() - fpsTime >= 1000) {
				lfps = (fps + lfps) >> 1;
				fps = 0;
				fpsTime = System.currentTimeMillis();
			}
			fps++;
			
		}


		/** adjusts camera based on current mesh of skinned group. 
		 * camera looks at mid point of height and placed at a distance 
		 * such that group height occupies 2/3 of screen height. */
		protected void autoAdjustCamera() {
			float[] bb = calcBoundingBox();
			float groupHeight = bb[3] - bb[2];
	        cameraController.cameraRadius = calcDistance(world.getCamera(), frameBuffer, 
	        		frameBuffer.getHeight() / 1.5f , groupHeight);
	        cameraController.minCameraRadius = groupHeight / 10f;
	        cameraController.cameraTarget.y = (bb[3] + bb[2]) / 2; 
	        cameraController.placeCamera();
		}

		/** calculates and returns whole bounding box of skinned group */
		protected float[] calcBoundingBox() {
			float[] box = null;
			
			for (Animated3D skin : masterNinja) {
				float[] skinBB = skin.getMesh().getBoundingBox();
				
				if (box == null) {
					box = skinBB;
				} else {
					// x
					box[0] = Math.min(box[0], skinBB[0]);
					box[1] = Math.max(box[1], skinBB[1]);
					// y
					box[2] = Math.min(box[2], skinBB[2]);
					box[3] = Math.max(box[3], skinBB[3]);
					// z
					box[4] = Math.min(box[4], skinBB[4]);
					box[5] = Math.max(box[5], skinBB[5]);
				}
			}
			return box;
		}
		
	    /** 
	     * calculates a camera distance to make object look height pixels on screen 
	     * @author EgonOlsen 
	     * */
	    protected float calcDistance(Camera c, FrameBuffer buffer, float height, float objectHeight) {
	        float h = height / 2f;
	        float os = objectHeight / 2f;

	        Camera cam = new Camera();
	        cam.setFOV(c.getFOV());
	        SimpleVector p1 = Interact2D.project3D2D(cam, buffer, new SimpleVector(0f, os, 1f));
	        float y1 = p1.y - buffer.getCenterY();
	        float z = (1f/h) * y1;

	        return z;
	    }
	}
	
}
