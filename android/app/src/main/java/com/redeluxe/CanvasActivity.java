package com.redeluxe;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CanvasActivity extends AppCompatActivity {
    private CanvasView canvasView;
    private ApiService apiService;
    private int currentCanvasId = 0;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_canvas);

        canvasView = findViewById(R.id.canvasView);
        apiService = new ApiService(this);

        // получаем ID canvas из intent
        currentCanvasId = getIntent().getIntExtra("canvas_id", 0);
        String canvasName = getIntent().getStringExtra("canvas_name");
        
        if (canvasName != null) {
            setTitle(canvasName);
        }

        setupButtons();
        
        if (currentCanvasId > 0) {
            loadCanvas();
        } else {
            // если ID не передан, используем демо контент
            canvasView.createDemoContent();
        }
    }

    private void setupButtons() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveButton).setOnClickListener(v -> saveCanvas());
        findViewById(R.id.addNoteButton).setOnClickListener(v -> showAddNoteDialog());
        findViewById(R.id.addTextButton).setOnClickListener(v -> showAddTextDialog());
        findViewById(R.id.addGroupButton).setOnClickListener(v -> canvasView.addGroup());
        
        // zoom controls
        findViewById(R.id.zoomInButton).setOnClickListener(v -> canvasView.zoomIn());
        findViewById(R.id.zoomOutButton).setOnClickListener(v -> canvasView.zoomOut());
        findViewById(R.id.fitButton).setOnClickListener(v -> canvasView.fitToScreen());
    }

    private void showAddNoteDialog() {
        EditText titleInput = new EditText(this);
        titleInput.setHint("заголовок заметки");
        titleInput.setTextColor(Color.WHITE);
        
        EditText contentInput = new EditText(this);
        contentInput.setHint("содержание");
        contentInput.setTextColor(Color.WHITE);
        contentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(titleInput);
        layout.addView(contentInput);
        
        new AlertDialog.Builder(this)
            .setTitle("новая заметка")
            .setView(layout)
            .setPositiveButton("создать", (dialog, which) -> {
                String title = titleInput.getText().toString().trim();
                String content = contentInput.getText().toString().trim();
                if (!title.isEmpty()) {
                    canvasView.addNote(title, content);
                }
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showAddTextDialog() {
        EditText textInput = new EditText(this);
        textInput.setHint("введите текст");
        textInput.setTextColor(Color.WHITE);
        
        new AlertDialog.Builder(this)
            .setTitle("добавить текст")
            .setView(textInput)
            .setPositiveButton("добавить", (dialog, which) -> {
                String text = textInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    canvasView.addText(text);
                }
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void loadCanvas() {
        apiService.getCanvas(currentCanvasId, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    canvasView.loadCanvasFromJson(result);
                } catch (Exception e) {
                    // если canvas не найден, создаем новый
                    createNewCanvas();
                }
            }

            @Override
            public void onError(String error) {
                createNewCanvas();
            }
        });
    }

    private void createNewCanvas() {
        apiService.createCanvas("новый canvas", 1920, 1080, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    currentCanvasId = json.getInt("id");
                    Toast.makeText(CanvasActivity.this, "canvas создан", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Toast.makeText(CanvasActivity.this, "ошибка создания canvas", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CanvasActivity.this, "ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveCanvas() {
        if (canvasView != null) {
            CanvasView.ViewportState state = canvasView.getViewportState();
            JSONArray nodesJsonArray = canvasView.getNodesAsJsonArray();
            JSONArray connectionsJsonArray = canvasView.getConnectionsAsJsonArray();
            String canvasData = canvasView.getCanvasData();
            
            apiService.saveCanvasState(currentCanvasId, state.zoom, state.panX, state.panY, 
                state.viewState, canvasData, nodesJsonArray, connectionsJsonArray, new ApiService.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(CanvasActivity.this, "canvas сохранен", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(CanvasActivity.this, "ошибка сохранения: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    

    public static class CanvasView extends View {
        private List<CanvasNode> nodes = new ArrayList<>();
        private List<CanvasConnection> connections = new ArrayList<>();
        private Paint nodePaint, textPaint, borderPaint, linePaint, backgroundPaint;
        private CanvasNode selectedNode = null;
        private CanvasNode draggedNode = null;
        private boolean isConnecting = false;
        private CanvasNode connectStart = null;
        
        // long press detection
        private Handler longPressHandler = new Handler();
        private Runnable longPressRunnable;
        private boolean isLongPress = false;
        private static final int LONG_PRESS_DELAY = 500;
        
        // zoom and pan
        private Matrix matrix = new Matrix();
        private Matrix savedMatrix = new Matrix();
        private ScaleGestureDetector scaleDetector;
        private float scaleFactor = 1.0f;
        private PointF start = new PointF();
        private PointF mid = new PointF();
        private int mode = NONE;
        
        private static final int NONE = 0;
        private static final int DRAG = 1;
        private static final int ZOOM = 2;
        
        private float lastTouchX, lastTouchY;

        public CanvasView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            initPaints();
            scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
            createDemoContent();
        }

        private void initPaints() {
            nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            nodePaint.setStyle(Paint.Style.FILL);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(28);

            borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);

            linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setColor(0xFF666666);
            linePaint.setStrokeWidth(4);
            linePaint.setAlpha(180);
            
            backgroundPaint = new Paint();
            backgroundPaint.setColor(0xFF0a0a0a);
        }

        private void createDemoContent() {
            // главная заметка
            CanvasNode main = new CanvasNode();
            main.type = "note";
            main.x = 300;
            main.y = 200;
            main.width = 220;
            main.height = 160;
            main.title = "главная идея";
            main.content = "центральная заметка\ncanvas системы";
            main.color = "#00ffff";
            nodes.add(main);

            // связанная заметка
            CanvasNode sub = new CanvasNode();
            sub.type = "note";
            sub.x = 600;
            sub.y = 100;
            sub.width = 180;
            sub.height = 120;
            sub.title = "детали";
            sub.content = "подробности\nреализации";
            sub.color = "#ff0080";
            nodes.add(sub);

            // текстовый блок
            CanvasNode text = new CanvasNode();
            text.type = "text";
            text.x = 100;
            text.y = 350;
            text.width = 160;
            text.height = 80;
            text.title = "";
            text.content = "важное замечание";
            text.color = "#00ff41";
            nodes.add(text);

            // связи
            connections.add(new CanvasConnection(main, sub, "relates"));
            connections.add(new CanvasConnection(main, text, "note"));
        }

        public void addNote(String title, String content) {
            CanvasNode node = new CanvasNode();
            node.type = "note";
            node.x = 150 + (float)(Math.random() * 400);
            node.y = 150 + (float)(Math.random() * 300);
            node.width = 200;
            node.height = 140;
            node.title = title;
            node.content = content;
            node.color = getRandomColor();
            nodes.add(node);
            invalidate();
        }

        public void addText(String text) {
            CanvasNode node = new CanvasNode();
            node.type = "text";
            node.x = 150 + (float)(Math.random() * 400);
            node.y = 150 + (float)(Math.random() * 300);
            node.width = 160;
            node.height = 80;
            node.title = "";
            node.content = text;
            node.color = getRandomColor();
            nodes.add(node);
            invalidate();
        }

        public void addGroup() {
            CanvasNode node = new CanvasNode();
            node.type = "group";
            node.x = 100 + (float)(Math.random() * 300);
            node.y = 100 + (float)(Math.random() * 200);
            node.width = 300;
            node.height = 200;
            node.title = "группа";
            node.content = "";
            node.color = "#333333";
            nodes.add(node);
            invalidate();
        }

        private String getRandomColor() {
            String[] colors = {"#00ffff", "#ff0080", "#00ff41", "#ff8000", "#8000ff"};
            return colors[(int)(Math.random() * colors.length)];
        }

        public void zoomIn() {
            scaleFactor *= 1.2f;
            matrix.setScale(scaleFactor, scaleFactor);
            invalidate();
        }

        public void zoomOut() {
            scaleFactor /= 1.2f;
            matrix.setScale(scaleFactor, scaleFactor);
            invalidate();
        }

        public void fitToScreen() {
            scaleFactor = 1.0f;
            matrix.reset();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            // черный фон
            canvas.drawPaint(backgroundPaint);
            
            canvas.save();
            canvas.concat(matrix);

            // рисуем связи
            for (CanvasConnection conn : connections) {
                drawConnection(canvas, conn);
            }

            // рисуем узлы
            for (CanvasNode node : nodes) {
                drawNode(canvas, node);
            }

            canvas.restore();
        }

        private void drawConnection(Canvas canvas, CanvasConnection conn) {
            CanvasNode from = conn.from;
            CanvasNode to = conn.to;
            
            float startX = from.x + from.width / 2;
            float startY = from.y + from.height / 2;
            float endX = to.x + to.width / 2;
            float endY = to.y + to.height / 2;
            
            // curved line
            Path path = new Path();
            path.moveTo(startX, startY);
            
            float ctrlX = (startX + endX) / 2;
            float ctrlY = Math.min(startY, endY) - 50;
            
            path.quadTo(ctrlX, ctrlY, endX, endY);
            
            linePaint.setColor(Color.parseColor("#666666"));
            canvas.drawPath(path, linePaint);
            
            // arrow head
            drawArrowHead(canvas, endX, endY, startX, startY);
        }

        private void drawArrowHead(Canvas canvas, float x, float y, float fromX, float fromY) {
            float angle = (float) Math.atan2(y - fromY, x - fromX);
            float arrowLength = 20;
            float arrowAngle = 0.5f;
            
            float x1 = x - arrowLength * (float) Math.cos(angle - arrowAngle);
            float y1 = y - arrowLength * (float) Math.sin(angle - arrowAngle);
            float x2 = x - arrowLength * (float) Math.cos(angle + arrowAngle);
            float y2 = y - arrowLength * (float) Math.sin(angle + arrowAngle);
            
            Path arrow = new Path();
            arrow.moveTo(x, y);
            arrow.lineTo(x1, y1);
            arrow.lineTo(x2, y2);
            arrow.close();
            
            linePaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(arrow, linePaint);
            linePaint.setStyle(Paint.Style.STROKE);
        }

        private void drawNode(Canvas canvas, CanvasNode node) {
            RectF rect = new RectF(node.x, node.y, node.x + node.width, node.y + node.height);

            try {
                nodePaint.setColor(Color.parseColor(node.color));
            } catch (Exception e) {
                nodePaint.setColor(Color.parseColor("#00ffff"));
            }

            // фон с альфой
            int alpha = node.type.equals("group") ? 80 : 220;
            nodePaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, 16, 16, nodePaint);

            // border
            borderPaint.setColor(node == selectedNode ? Color.WHITE : Color.parseColor(node.color));
            borderPaint.setStrokeWidth(node == selectedNode ? 5 : 3);
            canvas.drawRoundRect(rect, 16, 16, borderPaint);

            // текст
            textPaint.setColor(Color.WHITE);
            
            // заголовок
            if (!node.title.isEmpty()) {
                textPaint.setTextSize(30);
                textPaint.setFakeBoldText(true);
                canvas.drawText(node.title, node.x + 16, node.y + 40, textPaint);
                textPaint.setFakeBoldText(false);
            }

            // содержание
            if (!node.content.isEmpty()) {
                textPaint.setTextSize(24);
                String[] lines = node.content.split("\\n");
                float startY = node.title.isEmpty() ? node.y + 35 : node.y + 75;
                
                for (int i = 0; i < Math.min(lines.length, 4); i++) {
                    String line = lines[i];
                    if (line.length() > 22) {
                        line = line.substring(0, 22) + "...";
                    }
                    canvas.drawText(line, node.x + 16, startY + i * 28, textPaint);
                }
            }

            // type icon
            textPaint.setTextSize(24);
            String icon = getNodeIcon(node.type);
            canvas.drawText(icon, node.x + node.width - 35, node.y + 30, textPaint);
        }

        private String getNodeIcon(String type) {
            switch (type) {
                case "note": return "📝";
                case "text": return "💬";
                case "group": return "📁";
                case "image": return "🖼️";
                default: return "◇";
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            
            float x = event.getX();
            float y = event.getY();
            
            // transform touch coordinates
            float[] coords = {x, y};
            Matrix inverse = new Matrix();
            matrix.invert(inverse);
            inverse.mapPoints(coords);
            x = coords[0];
            y = coords[1];

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    isLongPress = false;
                    
                    draggedNode = findNodeAtPosition(x, y);
                    if (draggedNode != null) {
                        selectedNode = draggedNode;
                        lastTouchX = x;
                        lastTouchY = y;
                        
                        // setup long press detection
                        final CanvasNode nodeForLongPress = draggedNode;
                        longPressRunnable = () -> {
                            isLongPress = true;
                            showNodeEditDialog(nodeForLongPress);
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY);
                        
                        invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    float dx = event.getX(0) - event.getX(1);
                    float dy = event.getY(0) - event.getY(1);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > 10f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event);
                        mode = ZOOM;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    // cancel long press on move
                    if (longPressRunnable != null) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    
                    if (mode == DRAG && !isLongPress) {
                        if (draggedNode != null) {
                            float deltaX = x - lastTouchX;
                            float deltaY = y - lastTouchY;
                            
                            draggedNode.x += deltaX;
                            draggedNode.y += deltaY;
                            
                            lastTouchX = x;
                            lastTouchY = y;
                            
                            invalidate();
                            return true;
                        } else {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                            invalidate();
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    // cancel long press
                    if (longPressRunnable != null) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    
                    mode = NONE;
                    draggedNode = null;
                    isLongPress = false;
                    break;
            }

            return true;
        }

        private void midPoint(PointF point, MotionEvent event) {
            float x = event.getX(0) + event.getX(1);
            float y = event.getY(0) + event.getY(1);
            point.set(x / 2, y / 2);
        }

        private CanvasNode findNodeAtPosition(float x, float y) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                CanvasNode node = nodes.get(i);
                if (x >= node.x && x <= node.x + node.width &&
                    y >= node.y && y <= node.y + node.height) {
                    return node;
                }
            }
            return null;
        }

        public void showNodeEditDialog(CanvasNode node) {
            String[] options = {
                "📝 редактировать",
                "🎨 изменить цвет", 
                "🔗 создать связь",
                "📋 дублировать",
                "🗑️ удалить"
            };
            
            new AlertDialog.Builder(getContext())
                .setTitle(node.title.isEmpty() ? "узел" : node.title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: editNodeContent(node); break;
                        case 1: changeNodeColor(node); break;
                        case 2: startConnection(node); break;
                        case 3: duplicateNode(node); break;
                        case 4: deleteNode(node); break;
                    }
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void editNodeContent(CanvasNode node) {
            EditText titleInput = new EditText(getContext());
            titleInput.setHint("заголовок");
            titleInput.setText(node.title);
            titleInput.setTextColor(Color.WHITE);
            
            EditText contentInput = new EditText(getContext());
            contentInput.setHint("содержание");
            contentInput.setText(node.content);
            contentInput.setTextColor(Color.WHITE);
            contentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(titleInput);
            layout.addView(contentInput);
            
            new AlertDialog.Builder(getContext())
                .setTitle("редактировать " + getNodeIcon(node.type))
                .setView(layout)
                .setPositiveButton("сохранить", (dialog, which) -> {
                    node.title = titleInput.getText().toString().trim();
                    node.content = contentInput.getText().toString().trim();
                    invalidate();
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void changeNodeColor(CanvasNode node) {
            String[] colors = {"#00ffff", "#ff0080", "#00ff41", "#ff8000", "#8000ff", "#ffff00"};
            String[] colorNames = {"cyan", "pink", "green", "orange", "purple", "yellow"};
            
            new AlertDialog.Builder(getContext())
                .setTitle("выбрать цвет")
                .setItems(colorNames, (dialog, which) -> {
                    node.color = colors[which];
                    invalidate();
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void startConnection(CanvasNode node) {
            if (!isConnecting) {
                isConnecting = true;
                connectStart = node;
                Toast.makeText(getContext(), "выберите целевой узел", Toast.LENGTH_SHORT).show();
            } else {
                if (connectStart != node) {
                    connections.add(new CanvasConnection(connectStart, node, "связь"));
                    Toast.makeText(getContext(), "связь создана", Toast.LENGTH_SHORT).show();
                }
                isConnecting = false;
                connectStart = null;
                invalidate();
            }
        }
        
        private void duplicateNode(CanvasNode original) {
            CanvasNode copy = new CanvasNode();
            copy.type = original.type;
            copy.x = original.x + 50;
            copy.y = original.y + 50;
            copy.width = original.width;
            copy.height = original.height;
            copy.title = original.title + " (копия)";
            copy.content = original.content;
            copy.color = original.color;
            nodes.add(copy);
            invalidate();
        }
        
        private void deleteNode(CanvasNode node) {
            new AlertDialog.Builder(getContext())
                .setTitle("удалить узел?")
                .setMessage(node.title.isEmpty() ? "узел будет удален" : "\"" + node.title + "\" будет удален")
                .setPositiveButton("удалить", (dialog, which) -> {
                    nodes.remove(node);
                    // удаляем связи с этим узлом
                    connections.removeIf(conn -> conn.from == node || conn.to == node);
                    if (selectedNode == node) selectedNode = null;
                    invalidate();
                })
                .setNegativeButton("отмена", null)
                .show();
        }

        public ViewportState getViewportState() {
            ViewportState state = new ViewportState();
            state.zoom = scaleFactor;
            float[] values = new float[9];
            matrix.getValues(values);
            state.panX = values[Matrix.MTRANS_X];
            state.panY = values[Matrix.MTRANS_Y];
            state.viewState = String.format("{\"scale\":%.2f,\"translateX\":%.2f,\"translateY\":%.2f}", 
                scaleFactor, state.panX, state.panY);
            return state;
        }

        public JSONArray getNodesAsJsonArray() {
            JSONArray jsonArray = new JSONArray();
            for (CanvasNode node : nodes) {
                try {
                    JSONObject jsonNode = new JSONObject();
                    jsonNode.put("type", node.type);
                    jsonNode.put("x", (double)node.x);
                    jsonNode.put("y", (double)node.y);
                    jsonNode.put("width", (double)node.width);
                    jsonNode.put("height", (double)node.height);
                    jsonNode.put("rotation", 0.0);
                    jsonNode.put("scale", 1.0);
                    jsonNode.put("z_index", 0);
                    
                    // простые поля вместо JSON объектов
                    jsonNode.put("title", node.title);
                    jsonNode.put("content", node.content);
                    jsonNode.put("color", node.color);
                    
                    if (node.noteId > 0) {
                        jsonNode.put("note_id", node.noteId);
                    }
                    
                    jsonArray.put(jsonNode);
                } catch (JSONException e) {
                    // пропустить узел при ошибке
                }
            }
            return jsonArray;
        }

        public JSONArray getConnectionsAsJsonArray() {
            JSONArray jsonArray = new JSONArray();
            for (CanvasConnection conn : connections) {
                try {
                    JSONObject jsonConn = new JSONObject();
                    // ищем индексы узлов
                    int fromIndex = nodes.indexOf(conn.from);
                    int toIndex = nodes.indexOf(conn.to);
                    
                    if (fromIndex >= 0 && toIndex >= 0) {
                        jsonConn.put("from_node_id", fromIndex); // временно используем индекс
                        jsonConn.put("to_node_id", toIndex);
                        jsonConn.put("type", conn.type);
                        jsonArray.put(jsonConn);
                    }
                } catch (JSONException e) {
                    // пропустить связь при ошибке
                }
            }
            return jsonArray;
        }

        public String getCanvasData() {
            try {
                JSONObject data = new JSONObject();
                data.put("version", "1.0");
                data.put("nodes_count", nodes.size());
                data.put("connections_count", connections.size());
                data.put("created_at", System.currentTimeMillis());
                return data.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        public void loadCanvasFromJson(String jsonString) {
            try {
                JSONObject canvas = new JSONObject(jsonString);
                
                // загружаем viewport состояние
                if (canvas.has("zoom")) {
                    scaleFactor = (float) canvas.getDouble("zoom");
                }
                if (canvas.has("pan_x") && canvas.has("pan_y")) {
                    float panX = (float) canvas.getDouble("pan_x");
                    float panY = (float) canvas.getDouble("pan_y");
                    matrix.setTranslate(panX, panY);
                    matrix.postScale(scaleFactor, scaleFactor);
                }

                // загружаем узлы
                nodes.clear();
                if (canvas.has("nodes")) {
                    JSONArray nodesArray = canvas.getJSONArray("nodes");
                    for (int i = 0; i < nodesArray.length(); i++) {
                        JSONObject nodeJson = nodesArray.getJSONObject(i);
                        CanvasNode node = new CanvasNode();
                        node.type = nodeJson.optString("type", "note");
                        node.x = (float) nodeJson.optDouble("x", 100);
                        node.y = (float) nodeJson.optDouble("y", 100);
                        node.width = (float) nodeJson.optDouble("width", 200);
                        node.height = (float) nodeJson.optDouble("height", 140);
                        
                        // новый формат - простые поля
                        node.title = nodeJson.optString("title", "");
                        node.content = nodeJson.optString("content", "");
                        node.color = nodeJson.optString("color", "#00ffff");
                        node.noteId = nodeJson.optInt("note_id", 0);
                        
                        nodes.add(node);
                    }
                }

                // загружаем связи
                connections.clear();
                if (canvas.has("connections")) {
                    JSONArray connectionsArray = canvas.getJSONArray("connections");
                    for (int i = 0; i < connectionsArray.length(); i++) {
                        JSONObject connJson = connectionsArray.getJSONObject(i);
                        int fromIndex = connJson.optInt("from_node_id", -1);
                        int toIndex = connJson.optInt("to_node_id", -1);
                        String type = connJson.optString("type", "connection");
                        
                        if (fromIndex >= 0 && toIndex >= 0 && 
                            fromIndex < nodes.size() && toIndex < nodes.size()) {
                            CanvasConnection connection = new CanvasConnection(
                                nodes.get(fromIndex), 
                                nodes.get(toIndex), 
                                type
                            );
                            connections.add(connection);
                        }
                    }
                }

                invalidate();
            } catch (JSONException e) {
                // ошибка парсинга - используем демо контент
                createDemoContent();
            }
        }

        public List<CanvasNode> getNodes() {
            return nodes;
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
                matrix.setScale(scaleFactor, scaleFactor);
                invalidate();
                return true;
            }
        }

        public static class ViewportState {
            public double zoom = 1.0;
            public double panX = 0.0;
            public double panY = 0.0;
            public String viewState = "{}";
        }
    }

    public static class CanvasNode {
        public String type = "note";
        public float x, y, width, height;
        public String title = "";
        public String content = "";
        public String color = "#00ffff";
        public int noteId = 0;
    }

    public static class CanvasConnection {
        public CanvasNode from;
        public CanvasNode to;
        public String type;

        public CanvasConnection(CanvasNode from, CanvasNode to, String type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }
    }
} 