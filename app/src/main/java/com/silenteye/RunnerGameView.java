package com.silenteye;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RunnerGameView extends View {

    private static final int STATE_WAITING = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_DEAD = 2;
    private int gameState = STATE_WAITING;

    // --- Safe Insets ---
    private int insetTop = 0;
    private int insetBottom = 0;

    // --- Dimensions ---
    private float groundY;
    private float screenW;

    // --- Player ---
    private float playerX;
    private float playerY;
    private float playerSize = 60f;
    private float velocityY = 0f;
    private final float GRAVITY = 2.4f;
    private final float JUMP_VEL = -40f;
    private boolean onGround = true;
    private int jumpsRemaining = 2; // For double jump

    // --- Obstacles ---
    private final List<RectF> obstacles = new ArrayList<>();
    private float obstacleSpeed = 14f;
    private int framesSinceLastObstacle = 0;
    private int obstacleInterval = 80;
    private final Random random = new Random();

    // --- Particles & Effects ---
    private final List<Particle> particles = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private float screenShakeX = 0f;
    private float screenShakeY = 0f;
    private int shakeFrames = 0;

    // --- Score ---
    private int score = 0;
    private int highScore = 0;
    private int frameCount = 0;
    private int lastBoostScore = 0;
    private int boostFrames = 0;
    private int availableBoosts = 0;
    private SharedPreferences prefs;

    // --- Loop ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            handler.postDelayed(this, 16); // ~60fps
        }
    };

    // --- Paints ---
    private final Paint groundPaint = new Paint();
    private final Paint playerPaint = new Paint();
    private final Paint obstaclePaint = new Paint();
    private final Paint scorePaint = new Paint();
    private final Paint titlePaint = new Paint();
    private final Paint subtitlePaint = new Paint();
    private final Paint bgPaint = new Paint();
    private final Paint glowPaint = new Paint();
    private final Paint starPaint = new Paint();
    private final Paint gridPaint = new Paint();

    // Neon color scheme
    private static final int NEON_GREEN = 0xFF00FF88;
    private static final int NEON_CYAN = 0xFF00FFEE;
    private static final int NEON_RED = 0xFFFF3355;
    private static final int BG_COLOR = 0xFF0A0A14;
    private static final int GROUND_COLOR = 0xFF1A1A2E;

    public RunnerGameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("echo_runner", Context.MODE_PRIVATE);
        highScore = prefs.getInt("high_score", 0);
        setupPaints();
    }

    public void setSafeInsets(int top, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;
    }

    private void setupPaints() {
        bgPaint.setColor(BG_COLOR);

        groundPaint.setColor(GROUND_COLOR);
        groundPaint.setStrokeWidth(4f);
        groundPaint.setStyle(Paint.Style.FILL);

        playerPaint.setColor(NEON_GREEN);
        playerPaint.setStyle(Paint.Style.FILL);
        playerPaint.setShadowLayer(25, 0, 0, NEON_GREEN);

        glowPaint.setColor(NEON_GREEN);
        glowPaint.setAlpha(60);
        glowPaint.setStyle(Paint.Style.FILL);

        obstaclePaint.setColor(NEON_RED);
        obstaclePaint.setStyle(Paint.Style.FILL);
        obstaclePaint.setShadowLayer(20, 0, 0, NEON_RED);

        scorePaint.setColor(NEON_CYAN);
        scorePaint.setTextSize(58f);
        scorePaint.setFakeBoldText(true);
        scorePaint.setAntiAlias(true);
        scorePaint.setShadowLayer(10, 0, 0, NEON_CYAN);

        titlePaint.setColor(NEON_GREEN);
        titlePaint.setTextSize(92f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
        titlePaint.setShadowLayer(35, 0, 0, NEON_GREEN);

        subtitlePaint.setColor(Color.WHITE);
        subtitlePaint.setTextSize(48f);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint.setAntiAlias(true);
        subtitlePaint.setAlpha(200);

        starPaint.setColor(0x88FFFFFF);
        starPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0xFF161626);
        gridPaint.setStrokeWidth(3f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w;
        groundY = h * 0.75f;
        playerX = w * 0.2f;
        playerY = groundY - playerSize;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // Init stars for parallax
        stars.clear();
        for (int i = 0; i < 50; i++) {
            stars.add(new Star(random.nextFloat() * w, random.nextFloat() * groundY, random.nextFloat() * 2 + 1));
        }
    }

    private void startGame() {
        score = 0;
        frameCount = 0;
        lastBoostScore = 0;
        boostFrames = 0;
        availableBoosts = 0;
        obstacleSpeed = 15f;
        obstacleInterval = 80;
        obstacles.clear();
        particles.clear();
        playerY = groundY - playerSize;
        velocityY = 0f;
        onGround = true;
        jumpsRemaining = 2;
        shakeFrames = 0;
        screenShakeX = 0;
        screenShakeY = 0;
        gameState = STATE_PLAYING;
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void update() {
        if (gameState != STATE_PLAYING) return;

        frameCount++;
        score = frameCount / 6;

        // Gain a boost every 100 points
        if (score >= lastBoostScore + 100 && score > 0) {
            lastBoostScore += 100;
            availableBoosts++;
            spawnParticles(playerX + playerSize / 2, playerY + playerSize, 20, NEON_CYAN, true);
        }

        if (boostFrames == 0 && frameCount % 300 == 0) {
            obstacleSpeed = Math.min(obstacleSpeed + 1.5f, 32f);
            obstacleInterval = Math.max(obstacleInterval - 5, 45);
        }

        // Parallax stars
        for (Star s : stars) {
            s.x -= s.speed * (obstacleSpeed / 10f);
            if (s.x < 0) {
                s.x = screenW;
                s.y = random.nextFloat() * groundY;
            }
        }

        // Player physics
        if (boostFrames > 0) {
            boostFrames--;
            playerY = groundY - playerSize - 120f; // Fly above ground
            velocityY = 0f;
            onGround = false;
            jumpsRemaining = 2; // Always ready to jump when boost ends
            // Rocket exhaust particles
            spawnParticles(playerX + 10, playerY + playerSize, 3, 0xFFFF8800, false);
            spawnParticles(playerX + playerSize - 10, playerY + playerSize, 3, NEON_RED, false);
            
            // Temporary extreme speed
            obstacleSpeed = 40f;
        } else if (!onGround) {
            velocityY += GRAVITY;
            playerY += velocityY;
            if (playerY >= groundY - playerSize) {
                playerY = groundY - playerSize;
                velocityY = 0f;
                onGround = true;
                jumpsRemaining = 2;
                spawnParticles(playerX + playerSize / 2, groundY, 15, NEON_GREEN, true);
            }
        }

        // Spawn obstacles
        framesSinceLastObstacle++;
        if (framesSinceLastObstacle > obstacleInterval + random.nextInt(35)) {
            float obsH = 50 + random.nextInt(90);
            float obsW = 40 + random.nextInt(30);
            obstacles.add(new RectF(screenW + 10, groundY - obsH, screenW + 10 + obsW, groundY));
            framesSinceLastObstacle = 0;
        }

        // Move obstacles
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            RectF obs = obstacles.get(i);
            obs.left -= obstacleSpeed;
            obs.right -= obstacleSpeed;

            RectF playerRect = new RectF(
                    playerX + 10, playerY + 10,
                    playerX + playerSize - 10, playerY + playerSize - 10);

            if (boostFrames == 0 && RectF.intersects(playerRect, obs)) {
                killPlayer();
                return;
            }

            if (obs.right < 0) obstacles.remove(i);
        }

        // Update particles
        Iterator<Particle> pIt = particles.iterator();
        while (pIt.hasNext()) {
            Particle p = pIt.next();
            p.x += p.vx;
            p.y += p.vy;
            p.life -= 0.04f; // fade
            if (p.life <= 0) pIt.remove();
        }

        // Emit trail
        if (frameCount % 3 == 0) {
            spawnParticles(playerX, playerY + playerSize - 10, 1, NEON_CYAN, false);
        }
    }

    private void spawnParticles(float x, float y, int count, int color, boolean burst) {
        for (int i = 0; i < count; i++) {
            float vx = burst ? (random.nextFloat() * 10 - 5) : (-random.nextFloat() * 5 - 2);
            float vy = burst ? (-random.nextFloat() * 8) : (random.nextFloat() * 4 - 2);
            particles.add(new Particle(x, y, vx, vy, color));
        }
    }

    private void killPlayer() {
        gameState = STATE_DEAD;
        shakeFrames = 15;
        spawnParticles(playerX + playerSize / 2, playerY + playerSize / 2, 40, NEON_RED, true);

        handler.removeCallbacks(gameLoop);
        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt("high_score", highScore).apply();
        }

        // Keep animating particles for a bit during death
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (shakeFrames > 0) {
                    shakeFrames--;
                    screenShakeX = (random.nextFloat() * 30 - 15);
                    screenShakeY = (random.nextFloat() * 30 - 15);
                } else {
                    screenShakeX = 0;
                    screenShakeY = 0;
                }

                Iterator<Particle> pIt = particles.iterator();
                while (pIt.hasNext()) {
                    Particle p = pIt.next();
                    p.x += p.vx;
                    p.y += p.vy;
                    p.life -= 0.03f;
                    if (p.life <= 0) pIt.remove();
                }

                invalidate();

                if (shakeFrames > 0 || !particles.isEmpty()) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    private void jump() {
        if (jumpsRemaining > 0) {
            velocityY = JUMP_VEL;
            onGround = false;
            jumpsRemaining--;
            spawnParticles(playerX + playerSize / 2, playerY + playerSize, 10, NEON_CYAN, true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (gameState == STATE_WAITING || gameState == STATE_DEAD) {
                // Ignore touches until shake finishes
                if (shakeFrames == 0) startGame();
            } else if (gameState == STATE_PLAYING) {
                // Check if boost button pressed
                if (availableBoosts > 0 && boostFrames == 0) {
                    float bx = getWidth() - 150f;
                    float by = getHeight() - 150f;
                    float dx = event.getX() - bx;
                    float dy = event.getY() - by;
                    // If tap is within 120 pixels of the button center
                    if (dx * dx + dy * dy < 14400) {
                        availableBoosts--;
                        boostFrames = 180;
                        spawnParticles(playerX + playerSize / 2, playerY + playerSize, 50, NEON_RED, true);
                        return true;
                    }
                }
                jump();
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(screenShakeX, screenShakeY);

        // Background
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Parallax stars
        for (Star s : stars) {
            canvas.drawCircle(s.x, s.y, s.speed, starPaint);
        }

        // Grid lines (Perspective illusion)
        for (int x = 0; x < getWidth(); x += 150) {
            float shift = (frameCount * 2) % 150;
            canvas.drawLine(x - shift, groundY, x - shift - 300, getHeight(), gridPaint);
        }
        for (int y = (int) groundY; y < getHeight(); y += 40) {
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }

        // Ground
        canvas.drawRect(0, groundY, getWidth(), groundY + 6, playerPaint);
        
        // Only draw player if alive
        if (gameState != STATE_DEAD) {
            canvas.drawRoundRect(
                    playerX - 15, playerY - 15,
                    playerX + playerSize + 15, playerY + playerSize + 15,
                    30, 30, boostFrames > 0 ? obstaclePaint : glowPaint); // Red/orange glow during boost
            
            Paint activePlayerPaint = new Paint(playerPaint);
            if (boostFrames > 0) activePlayerPaint.setColor(0xFFFF8800); // Orange during boost
            
            canvas.drawRoundRect(
                    playerX, playerY,
                    playerX + playerSize, playerY + playerSize,
                    12, 12, activePlayerPaint);
        }

        // Obstacles
        for (RectF obs : obstacles) {
            canvas.drawRoundRect(obs, 8, 8, obstaclePaint);
        }

        // Particles
        Paint pPaint = new Paint();
        pPaint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            pPaint.setColor(p.color);
            pPaint.setAlpha((int) (p.life * 255));
            pPaint.setShadowLayer(10, 0, 0, p.color);
            canvas.drawCircle(p.x, p.y, 6 + (p.life * 4), pPaint);
        }

        canvas.restore();

        // UI text handles safe insets to avoid status bar overlap
        canvas.drawText("SCORE  " + score, 50, insetTop + 90, scorePaint);
        canvas.drawText("BEST   " + highScore, 50, insetTop + 160, scorePaint);
        
        // Double jump indicator
        if (gameState == STATE_PLAYING) {
            if (boostFrames > 0) {
                Paint boostIndPaint = new Paint();
                boostIndPaint.setColor(0xFFFF8800);
                boostIndPaint.setTextSize(60f);
                boostIndPaint.setFakeBoldText(true);
                boostIndPaint.setShadowLayer(20, 0, 0, NEON_RED);
                canvas.drawText("BOOST ACTIVE", getWidth() - 450, insetTop + 90, boostIndPaint);
            } else {
                Paint indPaint = new Paint();
                indPaint.setColor(jumpsRemaining == 2 ? NEON_GREEN : (jumpsRemaining == 1 ? NEON_CYAN : Color.DKGRAY));
                indPaint.setTextSize(40f);
                indPaint.setFakeBoldText(true);
                canvas.drawText("JUMPS: " + jumpsRemaining, getWidth() - 250, insetTop + 90, indPaint);
            }

            // Draw Boost Button if available
            if (availableBoosts > 0 && boostFrames == 0) {
                float bx = getWidth() - 150f;
                float by = getHeight() - 150f;
                
                Paint btnBgPaint = new Paint();
                btnBgPaint.setColor(NEON_RED);
                btnBgPaint.setAlpha(120);
                canvas.drawCircle(bx, by, 90f, btnBgPaint);
                
                Paint btnTextPaint = new Paint();
                btnTextPaint.setColor(Color.WHITE);
                btnTextPaint.setTextSize(36f);
                btnTextPaint.setFakeBoldText(true);
                btnTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("ROCKET", bx, by - 10, btnTextPaint);
                canvas.drawText("x" + availableBoosts, bx, by + 40, btnTextPaint);
            }
        }

        // Overlays
        if (gameState == STATE_WAITING) {
            drawOverlay(canvas, "ECHO RUNNER", "TAP TO START");
        } else if (gameState == STATE_DEAD && shakeFrames == 0) {
            drawOverlay(canvas, "SYSTEM FAILURE", "SCORE: " + score + "   •   TAP TO RETRY");
        }
    }

    private void drawOverlay(Canvas canvas, String title, String subtitle) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        Paint dimPaint = new Paint();
        dimPaint.setColor(0xD0050510);
        canvas.drawRoundRect(cx - 400, cy - 160, cx + 400, cy + 150, 40, 40, dimPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(gameState == STATE_DEAD ? NEON_RED : NEON_GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        canvas.drawRoundRect(cx - 400, cy - 160, cx + 400, cy + 150, 40, 40, borderPaint);

        Paint tPaint = new Paint(titlePaint);
        if (gameState == STATE_DEAD) tPaint.setColor(NEON_RED);

        canvas.drawText(title, cx, cy - 30, tPaint);
        canvas.drawText(subtitle, cx, cy + 80, subtitlePaint);
    }

    public void stopGame() {
        handler.removeCallbacks(gameLoop);
    }

    private static class Particle {
        float x, y, vx, vy, life = 1f;
        int color;
        Particle(float x, float y, float vx, float vy, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color;
        }
    }

    private static class Star {
        float x, y, speed;
        Star(float x, float y, float speed) {
            this.x = x; this.y = y; this.speed = speed;
        }
    }
}
