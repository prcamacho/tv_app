package ar.com.prcamacho.tvapp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(13, 27, 38);
    private static final int TEXT = Color.rgb(245, 248, 250);
    private final List<VideoItem> videos = new ArrayList<>();
    private final List<RoutineItem> routines = new ArrayList<>();
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private ListenerRegistration catalogListener;
    private LinearLayout root;
    private String screen = "home";
    private int viewToken = 0;
    private VideoView player;

    private static class VideoItem {
        String title, path;
        VideoItem(String title, String path) { this.title = title; this.path = path; }
    }
    private static class StepItem {
        String text, imagePath;
        StepItem(String text, String imagePath) { this.text = text; this.imagePath = imagePath; }
    }
    private static class RoutineItem {
        String title;
        List<StepItem> steps = new ArrayList<>();
        RoutineItem(String title) { this.title = title; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        if (auth.getCurrentUser() == null) showLogin("");
        else { showHome(); watchCatalog(); }
    }

    @Override protected void onDestroy() {
        if (catalogListener != null) catalogListener.remove();
        if (player != null) player.stopPlayback();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    private void newScreen(String name) {
        screen = name; viewToken++;
        if (player != null) { player.stopPlayback(); player = null; }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(38), dp(48), dp(38));
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(TEXT); view.setTextSize(size);
        view.setPadding(0, dp(8), 0, dp(12));
        return view;
    }
    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(16));
        return shape;
    }
    private Button button(String label, View.OnClickListener action) {
        Button view = new Button(this);
        view.setText(label); view.setAllCaps(false); view.setTextSize(24);
        view.setMinHeight(dp(76));
        view.setPadding(dp(22), dp(12), dp(22), dp(12));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, rounded(Color.rgb(245, 191, 107)));
        background.addState(new int[]{}, rounded(Color.rgb(35, 71, 90)));
        view.setBackground(background);
        view.setTextColor(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
            new int[]{BACKGROUND, TEXT}));
        view.setOnClickListener(action);
        LinearLayout.LayoutParams params = fullWidth(); params.bottomMargin = dp(14);
        view.setLayoutParams(params);
        return view;
    }
    private EditText field(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint(hint); input.setInputType(inputType);
        input.setTextSize(22); input.setTextColor(TEXT); input.setHintTextColor(Color.LTGRAY);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(rounded(Color.rgb(35, 71, 90)));
        LinearLayout.LayoutParams params = fullWidth(); params.bottomMargin = dp(18);
        input.setLayoutParams(params);
        return input;
    }
    private ScrollView scrollingList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return scroll;
    }
    private LinearLayout column(ScrollView scroll) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        return column;
    }

    private void showLogin(String error) {
        newScreen("login");
        root.addView(text("TV en familia", 38));
        root.addView(text("Ingreso inicial para adultos", 25));
        root.addView(text("Ingresá una sola vez con la cuenta de lectura creada para esta TV.", 20));
        EditText email = field("Correo de la TV", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(email); root.addView(password);
        TextView status = text(error, 19); root.addView(status);
        root.addView(button("Ingresar", v -> {
            status.setText("Ingresando…");
            auth.signInWithEmailAndPassword(email.getText().toString().trim(), password.getText().toString())
                .addOnSuccessListener(result -> { showHome(); watchCatalog(); })
                .addOnFailureListener(ex -> status.setText("No se pudo ingresar. Revisá los datos o la conexión."));
        }));
    }

    private void watchCatalog() {
        if (catalogListener != null) catalogListener.remove();
        catalogListener = firestore.collection("catalog").document("current")
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    if (screen.equals("home")) showMessage("No se pudo cargar el contenido. Revisá los permisos y la conexión.");
                    return;
                }
                if (snapshot != null) parseCatalog(snapshot);
                if (screen.equals("home")) showHome();
            });
    }
    private static String string(Object value) { return value instanceof String ? (String) value : ""; }
    private void parseCatalog(DocumentSnapshot snapshot) {
        videos.clear(); routines.clear();
        Object videoData = snapshot.get("videos");
        if (videoData instanceof List<?>) for (Object item : (List<?>) videoData) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            String path = string(map.get("storagePath"));
            if (path.startsWith("videos/")) videos.add(new VideoItem(string(map.get("title")), path));
        }
        Object routineData = snapshot.get("routines");
        if (routineData instanceof List<?>) for (Object item : (List<?>) routineData) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            RoutineItem routine = new RoutineItem(string(map.get("title")));
            Object stepData = map.get("steps");
            if (stepData instanceof List<?>) for (Object rawStep : (List<?>) stepData) {
                if (!(rawStep instanceof Map<?, ?>)) continue;
                Map<?, ?> step = (Map<?, ?>) rawStep;
                routine.steps.add(new StepItem(string(step.get("text")), string(step.get("imagePath"))));
            }
            if (!routine.steps.isEmpty()) routines.add(routine);
        }
        cleanOldFiles();
    }
    private void cleanOldFiles() {
        Set<String> wanted = new HashSet<>();
        for (VideoItem video : videos) wanted.add(cacheName(video.path));
        for (RoutineItem routine : routines) for (StepItem step : routine.steps)
            if (step.imagePath.startsWith("images/")) wanted.add(cacheName(step.imagePath));
        File[] files = getFilesDir().listFiles();
        if (files != null) for (File file : files)
            if (file.getName().startsWith("media_") && !wanted.contains(file.getName())) file.delete();
    }
    private void showMessage(String message) { root.addView(text(message, 21)); }
    private void showHome() {
        newScreen("home");
        root.addView(text("TV en familia", 38));
        ScrollView scroll = scrollingList();
        LinearLayout list = column(scroll);
        list.addView(text("Videos", 28));
        if (videos.isEmpty()) list.addView(text("Todavía no hay videos.", 20));
        for (VideoItem video : videos) list.addView(button("▶  " + video.title, v -> openVideo(video)));
        list.addView(text("Rutinas", 28));
        if (routines.isEmpty()) list.addView(text("Todavía no hay rutinas.", 20));
        for (RoutineItem routine : routines) list.addView(button("▸  " + routine.title, v -> showStep(routine, 0)));
        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    private String cacheName(String path) { return "media_" + path.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private interface FileReady { void ready(File file); }
    private void loadFile(String path, int token, TextView status, FileReady ready) {
        File file = new File(getFilesDir(), cacheName(path));
        if (file.isFile() && file.length() > 0) { ready.ready(file); return; }
        status.setText("Descargando contenido…");
        storage.getReference().child(path).getFile(file)
            .addOnSuccessListener(task -> { if (viewToken == token) ready.ready(file); })
            .addOnFailureListener(ex -> { file.delete(); if (viewToken == token) status.setText("No se pudo descargar. Revisá la conexión."); });
    }
    private void openVideo(VideoItem video) {
        newScreen("video");
        root.addView(text(video.title, 29));
        TextView status = text("Preparando video…", 20); root.addView(status);
        VideoView view = new VideoView(this); player = view;
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button pause = button("Pausar / continuar", v -> { if (view.isPlaying()) view.pause(); else view.start(); });
        root.addView(pause);
        root.addView(button("Volver", v -> showHome()));
        int token = viewToken;
        loadFile(video.path, token, status, file -> {
            status.setText("");
            view.setVideoURI(Uri.fromFile(file));
            view.setOnPreparedListener(media -> view.start());
            view.setOnErrorListener((media, what, extra) -> { status.setText("La TV no pudo reproducir este MP4."); return true; });
            view.setOnCompletionListener(media -> showHome());
        });
        pause.requestFocus();
    }
    private void showStep(RoutineItem routine, int index) {
        newScreen("routine");
        StepItem step = routine.steps.get(index);
        root.addView(text(routine.title, 31));
        root.addView(text("Paso " + (index + 1) + " de " + routine.steps.size(), 20));
        TextView instruction = text(step.text, 35);
        instruction.setGravity(Gravity.CENTER);
        root.addView(instruction);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView status = text("", 18); root.addView(status);
        if (step.imagePath.startsWith("images/")) {
            int token = viewToken;
            loadFile(step.imagePath, token, status, file -> {
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
                image.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath(), options));
                status.setText("");
            });
        }
        if (index > 0) root.addView(button("Paso anterior", v -> showStep(routine, index - 1)));
        if (index < routine.steps.size() - 1) root.addView(button("Siguiente paso", v -> showStep(routine, index + 1)));
        else root.addView(button("Terminar", v -> showHome()));
        root.addView(button("Volver al inicio", v -> showHome()));
    }
    @Override public void onBackPressed() {
        if (screen.equals("video") || screen.equals("routine")) showHome();
        else super.onBackPressed();
    }
}
