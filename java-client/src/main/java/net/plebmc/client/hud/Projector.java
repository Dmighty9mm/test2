package net.plebmc.client.hud;

/**
 * World to GUI-pixel projection with the 1.8 camera: vertical field of
 * view, view = rotX(pitch) * rotY(yaw + 180) * translate(-eye), the same
 * transforms EntityRenderer.setupCameraTransform/orientCamera apply in
 * first person. Used to draw world-anchored Apollo markers on the HUD.
 */
final class Projector {

	double eyeX;
	double eyeY;
	double eyeZ;
	private double sinYaw;
	private double cosYaw;
	private double sinPitch;
	private double cosPitch;
	private double tanHalfFov;
	private double aspect;
	private int guiW;
	private int guiH;

	/** Projected GUI position of the last {@link #project} call. */
	float sx;
	float sy;
	/** Distance along the view axis of the last projection. */
	float depth;

	void setup(double eyeX, double eyeY, double eyeZ, float yawDeg, float pitchDeg, float fovDeg, int pxW, int pxH,
			int guiW, int guiH) {
		this.eyeX = eyeX;
		this.eyeY = eyeY;
		this.eyeZ = eyeZ;
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		sinYaw = Math.sin(yaw);
		cosYaw = Math.cos(yaw);
		sinPitch = Math.sin(pitch);
		cosPitch = Math.cos(pitch);
		tanHalfFov = Math.tan(Math.toRadians(Math.max(1f, Math.min(170f, fovDeg))) / 2.0);
		aspect = pxH > 0 ? (double) pxW / (double) pxH : 1.0;
		this.guiW = guiW;
		this.guiH = guiH;
	}

	// The world pass's own matrices (PlebEntities.renderGlow): exact in every
	// camera mode (third person, view bobbing, zoom), relative to the render origin.
	private static final float[] MV = new float[16];
	private static final float[] PROJ = new float[16];
	private static double originX, originY, originZ;
	private static boolean captured;
	private boolean matrices;

	/** Called from the world pass with GL's column-major modelview and projection. */
	static void capture(float[] modelview, float[] projection, double ox, double oy, double oz) {
		System.arraycopy(modelview, 0, MV, 0, 16);
		System.arraycopy(projection, 0, PROJ, 0, 16);
		originX = ox;
		originY = oy;
		originZ = oz;
		captured = true;
	}

	static boolean captured() {
		return captured;
	}

	static void forget() {
		captured = false;
	}

	/** Use the captured world matrices (when there are any) instead of the first person math. */
	void useMatrices(boolean on) {
		matrices = on && captured;
	}

	private boolean projectMatrices(double x, double y, double z) {
		double dx = x - originX, dy = y - originY, dz = z - originZ;
		double vx = MV[0] * dx + MV[4] * dy + MV[8] * dz + MV[12];
		double vy = MV[1] * dx + MV[5] * dy + MV[9] * dz + MV[13];
		double vz = MV[2] * dx + MV[6] * dy + MV[10] * dz + MV[14];
		double vw = MV[3] * dx + MV[7] * dy + MV[11] * dz + MV[15];
		double cx = PROJ[0] * vx + PROJ[4] * vy + PROJ[8] * vz + PROJ[12] * vw;
		double cy = PROJ[1] * vx + PROJ[5] * vy + PROJ[9] * vz + PROJ[13] * vw;
		double cw = PROJ[3] * vx + PROJ[7] * vy + PROJ[11] * vz + PROJ[15] * vw;
		depth = (float) cw;
		if (cw < 0.05) {
			return false;
		}
		sx = (float) ((cx / cw * 0.5 + 0.5) * guiW);
		sy = (float) ((1.0 - (cy / cw * 0.5 + 0.5)) * guiH);
		return true;
	}

	/** Projects a world point; false when it is behind the camera. */
	boolean project(double x, double y, double z) {
		if (matrices) {
			return projectMatrices(x, y, z);
		}
		double dx = x - eyeX;
		double dy = y - eyeY;
		double dz = z - eyeZ;
		// rotate about Y by (yaw + 180)
		double rx = -(dx * cosYaw + dz * sinYaw);
		double rz = dx * sinYaw - dz * cosYaw;
		// rotate about X by pitch
		double ry = dy * cosPitch - rz * sinPitch;
		double rz2 = dy * sinPitch + rz * cosPitch;
		double d = -rz2; // camera looks down -Z
		depth = (float) d;
		if (d < 0.05) {
			return false;
		}
		double ndcX = rx / (d * tanHalfFov * aspect);
		double ndcY = ry / (d * tanHalfFov);
		sx = (float) ((ndcX * 0.5 + 0.5) * guiW);
		sy = (float) ((1.0 - (ndcY * 0.5 + 0.5)) * guiH);
		return true;
	}

	int guiW() {
		return guiW;
	}

	int guiH() {
		return guiH;
	}

	double distance(double x, double y, double z) {
		double dx = x - eyeX;
		double dy = y - eyeY;
		double dz = z - eyeZ;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
