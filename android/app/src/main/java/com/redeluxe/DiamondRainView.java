package com.redeluxe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DiamondRainView extends View {
    private List<Diamond> diamonds;
    private Paint paint;
    private Random random;
    private int screenWidth, screenHeight;
    private float parallaxOffset = 0f;
    
    private static final int DIAMOND_COUNT = 40;
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 3f;
    private static final float MIN_SIZE = 4f;
    private static final float MAX_SIZE = 16f;
    
    private class Diamond {
        float x, y;
        float speed;
        float size;
        int alpha;
        float parallaxFactor;
        
        Diamond() {
            reset();
        }
        
        void reset() {
            x = random.nextFloat() * (screenWidth + 200) - 100;
            y = -size - random.nextFloat() * 200;
            speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
            size = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE);
            alpha = 20 + random.nextInt(100); // 20-120 alpha для лучшей видимости на черном
            parallaxFactor = 0.1f + random.nextFloat() * 0.9f; // разные слои глубины
        }
        
        void update() {
            // основное движение вниз
            y += speed;
            
            // паралакс эффект - ромбики на разных слоях двигаются с разной скоростью
            float parallaxX = parallaxOffset * parallaxFactor;
            
            // проверка выхода за границы
            if (y > screenHeight + size) {
                reset();
            }
            if (x + parallaxX < -size - 50 || x + parallaxX > screenWidth + size + 50) {
                x = random.nextFloat() * (screenWidth + 200) - 100;
            }
        }
        
        void draw(Canvas canvas) {
            paint.setAlpha(alpha);
            
            float drawX = x + (parallaxOffset * parallaxFactor);
            
            Path path = new Path();
            path.moveTo(drawX, y - size/2);
            path.lineTo(drawX + size/2, y);
            path.lineTo(drawX, y + size/2);
            path.lineTo(drawX - size/2, y);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
    
    public DiamondRainView(Context context) {
        super(context);
        init();
    }
    
    public DiamondRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF666666); // серый цвет для видимости на черном фоне
        random = new Random();
        diamonds = new ArrayList<>();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        screenHeight = h;
        
        diamonds.clear();
        for (int i = 0; i < DIAMOND_COUNT; i++) {
            diamonds.add(new Diamond());
        }
    }
    
    public void updateParallax(float sensorValue) {
        parallaxOffset += sensorValue * 0.1f; // плавное изменение от сенсора
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // обновляем паралакс эффект
        parallaxOffset += 0.3f;
        if (parallaxOffset > screenWidth * 2) {
            parallaxOffset = -screenWidth;
        }
        
        for (Diamond diamond : diamonds) {
            diamond.update();
            diamond.draw(canvas);
        }
        
        invalidate(); // продолжаем анимацию
    }
} 