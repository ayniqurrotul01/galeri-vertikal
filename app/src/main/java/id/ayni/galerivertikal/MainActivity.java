package id.ayni.galerivertikal;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int IZIN_GAMBAR = 401;
    private final ArrayList<Uri> semuaFoto = new ArrayList<>();
    private final ArrayList<Uri> urutanTerpilih = new ArrayList<>();
    private final LinkedHashSet<Uri> pilihan = new LinkedHashSet<>();
    private GalleryAdapter galleryAdapter;
    private LinearLayout bilahPilihan;
    private TextView jumlahPilihan;
    private TextView infoGaleri;
    private boolean sedangMembaca = false;
    private boolean layarPenuh = false;
    private LinearLayout bilahPembaca;
    private RecyclerView daftarPembaca;
    private ReaderAdapter readerAdapter;
    private int jarakDp = 0;

    private final ActivityResultLauncher<Intent> pemilihFoto =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), hasil -> {
                if (hasil.getResultCode() != RESULT_OK || hasil.getData() == null) return;
                Intent data = hasil.getData();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        tambahDariPemilih(data.getClipData().getItemAt(i).getUri(), data.getFlags());
                    }
                } else if (data.getData() != null) {
                    tambahDariPemilih(data.getData(), data.getFlags());
                }
                tampilkanPembaca();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            tampilkanGaleri();
            periksaIzinDanMuat();
        } catch (Throwable masalah) {
            tampilkanModeAman();
        }
    }

    private void tampilkanGaleri() {
        sedangMembaca = false;
        setLayarPenuh(false);
        LinearLayout akar = new LinearLayout(this);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setBackgroundColor(Color.WHITE);

        LinearLayout kepala = new LinearLayout(this);
        kepala.setGravity(Gravity.CENTER_VERTICAL);
        kepala.setPadding(dp(18), dp(12), dp(10), dp(10));

        TextView judul = teks("Galeri Vertikal", 24, Color.BLACK);
        judul.setTypeface(null, android.graphics.Typeface.BOLD);
        kepala.addView(judul, new LinearLayout.LayoutParams(0, dp(56), 1));

        Button tambah = tombol("Pilih foto");
        tambah.setOnClickListener(v -> bukaPemilihFoto());
        kepala.addView(tambah, new LinearLayout.LayoutParams(dp(108), dp(44)));
        akar.addView(kepala);

        TextView petunjuk = teks("Ketuk foto sesuai urutan yang kamu inginkan.", 14, Color.DKGRAY);
        petunjuk.setPadding(dp(18), 0, dp(18), dp(10));
        akar.addView(petunjuk);

        infoGaleri = teks("Memuat foto…", 14, Color.DKGRAY);
        infoGaleri.setGravity(Gravity.CENTER);
        infoGaleri.setPadding(dp(18), dp(8), dp(18), dp(12));
        akar.addView(infoGaleri);

        RecyclerView galeri = new RecyclerView(this);
        galeri.setLayoutManager(new GridLayoutManager(this, 3));
        galeri.setHasFixedSize(true);
        galeri.setItemViewCacheSize(6);
        galeri.setItemAnimator(null);
        galleryAdapter = new GalleryAdapter();
        galeri.setAdapter(galleryAdapter);
        akar.addView(galeri, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bilahPilihan = new LinearLayout(this);
        bilahPilihan.setGravity(Gravity.CENTER_VERTICAL);
        bilahPilihan.setPadding(dp(14), dp(10), dp(14), dp(10));
        bilahPilihan.setBackgroundColor(Color.BLACK);
        bilahPilihan.setVisibility(View.GONE);

        jumlahPilihan = teks("0 foto dipilih", 14, Color.WHITE);
        bilahPilihan.addView(jumlahPilihan, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button urutkan = tombolTerang("Atur urutan");
        urutkan.setOnClickListener(v -> bukaPengurutan());
        bilahPilihan.addView(urutkan, new LinearLayout.LayoutParams(dp(112), dp(44)));

        Button buka = tombolTerang("Buka vertikal");
        LinearLayout.LayoutParams pBuka = new LinearLayout.LayoutParams(dp(124), dp(44));
        pBuka.setMargins(dp(8), 0, 0, 0);
        buka.setOnClickListener(v -> tampilkanPembaca());
        bilahPilihan.addView(buka, pBuka);
        akar.addView(bilahPilihan);

        setContentView(akar);
        terapkanInsetSistem(akar, true);
    }

    private void periksaIzinDanMuat() {
        String izin = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        try {
            if (ContextCompat.checkSelfPermission(this, izin) == PackageManager.PERMISSION_GRANTED) {
                muatSemuaFoto();
            } else {
                if (infoGaleri != null) infoGaleri.setText("Izinkan akses agar semua foto tampil di sini.");
                ActivityCompat.requestPermissions(this, new String[]{izin}, IZIN_GAMBAR);
            }
        } catch (Exception masalah) {
            if (infoGaleri != null) infoGaleri.setText("Gunakan tombol Pilih foto untuk mulai.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int kode, @NonNull String[] izin,
                                           @NonNull int[] hasil) {
        super.onRequestPermissionsResult(kode, izin, hasil);
        if (kode == IZIN_GAMBAR && hasil.length > 0 && hasil[0] == PackageManager.PERMISSION_GRANTED) {
            muatSemuaFoto();
        } else {
            if (infoGaleri != null) infoGaleri.setText("Akses galeri tidak diberikan. Gunakan tombol Pilih foto.");
            Toast.makeText(this,
                    "Izin galeri ditolak. Kamu tetap bisa memakai tombol Pilih foto.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void muatSemuaFoto() {
        if (infoGaleri != null) infoGaleri.setText("Memuat foto…");
        new Thread(() -> {
            ArrayList<Uri> hasilFoto = new ArrayList<>();
            String pesanMasalah = null;
            try {
                Uri koleksi = Build.VERSION.SDK_INT >= 29
                        ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] kolom = {MediaStore.Images.Media._ID};
                try (Cursor cursor = getContentResolver().query(
                        koleksi, kolom, null, null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                    if (cursor != null) {
                        int indeksId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        while (cursor.moveToNext()) {
                            hasilFoto.add(ContentUris.withAppendedId(
                                    koleksi, cursor.getLong(indeksId)));
                        }
                    }
                }
            } catch (Exception masalah) {
                pesanMasalah = "Foto tidak bisa dimuat otomatis. Gunakan tombol Pilih foto.";
            }
            String pesanAkhir = pesanMasalah;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                semuaFoto.clear();
                semuaFoto.addAll(hasilFoto);
                if (galleryAdapter != null) galleryAdapter.notifyDataSetChanged();
                if (infoGaleri != null) {
                    if (pesanAkhir != null) infoGaleri.setText(pesanAkhir);
                    else if (hasilFoto.isEmpty()) infoGaleri.setText("Belum ada foto yang bisa ditampilkan.");
                    else infoGaleri.setVisibility(View.GONE);
                }
            });
        }, "muat-galeri").start();
    }

    private void tampilkanModeAman() {
        LinearLayout akar = new LinearLayout(this);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setGravity(Gravity.CENTER);
        akar.setPadding(dp(28), dp(28), dp(28), dp(28));
        akar.setBackgroundColor(Color.WHITE);
        TextView judul = teks("Galeri Vertikal", 25, Color.BLACK);
        judul.setTypeface(null, android.graphics.Typeface.BOLD);
        judul.setGravity(Gravity.CENTER);
        akar.addView(judul, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        TextView pesan = teks("Galeri otomatis tidak dapat dibuka di perangkat ini. Kamu tetap bisa memilih beberapa gambar.", 16, Color.DKGRAY);
        pesan.setGravity(Gravity.CENTER);
        pesan.setPadding(0, dp(8), 0, dp(24));
        akar.addView(pesan);
        Button pilih = tombol("Pilih foto");
        pilih.setOnClickListener(v -> bukaPemilihFoto());
        akar.addView(pilih, new LinearLayout.LayoutParams(dp(150), dp(50)));
        setContentView(akar);
        terapkanInsetSistem(akar, true);
    }

    private void bukaPemilihFoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pemilihFoto.launch(intent);
    }

    private void tambahDariPemilih(Uri uri, int flags) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        if (pilihan.add(uri)) urutanTerpilih.add(uri);
    }

    private void ubahPilihan(Uri uri) {
        if (pilihan.contains(uri)) {
            pilihan.remove(uri);
            urutanTerpilih.remove(uri);
        } else {
            pilihan.add(uri);
            urutanTerpilih.add(uri);
        }
        perbaruiBilahPilihan();
    }

    private void perbaruiBilahPilihan() {
        if (bilahPilihan == null) return;
        bilahPilihan.setVisibility(urutanTerpilih.isEmpty() ? View.GONE : View.VISIBLE);
        jumlahPilihan.setText(urutanTerpilih.size() + " foto dipilih");
    }

    private void bukaPengurutan() {
        if (urutanTerpilih.size() < 2) {
            Toast.makeText(this, "Pilih sedikitnya dua foto.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Uri> salinan = new ArrayList<>(urutanTerpilih);
        RecyclerView daftar = new RecyclerView(this);
        daftar.setLayoutManager(new LinearLayoutManager(this));
        ReorderAdapter adapter = new ReorderAdapter(salinan);
        daftar.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder dari,
                                            @NonNull RecyclerView.ViewHolder ke) {
                int a = dari.getBindingAdapterPosition();
                int b = ke.getBindingAdapterPosition();
                Collections.swap(salinan, a, b);
                adapter.notifyItemMoved(a, b);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int arah) { }
        });
        helper.attachToRecyclerView(daftar);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Atur urutan")
                .setMessage("Tekan dan geser foto ke atas atau bawah.")
                .setView(daftar)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    urutanTerpilih.clear();
                    urutanTerpilih.addAll(salinan);
                    pilihan.clear();
                    pilihan.addAll(salinan);
                    galleryAdapter.notifyDataSetChanged();
                }).create();
        dialog.setOnShowListener(d -> {
            ViewGroup.LayoutParams lp = daftar.getLayoutParams();
            if (lp != null) lp.height = dp(420);
        });
        dialog.show();
    }

    private void tampilkanPembaca() {
        if (urutanTerpilih.isEmpty()) {
            Toast.makeText(this, "Pilih foto terlebih dahulu.", Toast.LENGTH_SHORT).show();
            return;
        }
        sedangMembaca = true;
        layarPenuh = false;

        LinearLayout akar = new LinearLayout(this);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setBackgroundColor(Color.WHITE);

        bilahPembaca = new LinearLayout(this);
        bilahPembaca.setGravity(Gravity.CENTER_VERTICAL);
        bilahPembaca.setPadding(dp(8), dp(6), dp(8), dp(6));
        bilahPembaca.setBackgroundColor(Color.WHITE);

        Button kembali = tombol("‹");
        kembali.setTextSize(28);
        kembali.setOnClickListener(v -> tampilkanGaleri());
        bilahPembaca.addView(kembali, new LinearLayout.LayoutParams(dp(52), dp(46)));

        TextView judul = teks("Galeri Vertikal", 19, Color.BLACK);
        judul.setTypeface(null, android.graphics.Typeface.BOLD);
        bilahPembaca.addView(judul, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button jarak = tombol("Jarak: rapat");
        jarak.setOnClickListener(v -> {
            jarakDp = jarakDp == 0 ? 8 : (jarakDp == 8 ? 24 : 0);
            jarak.setText(jarakDp == 0 ? "Jarak: rapat" :
                    (jarakDp == 8 ? "Jarak: tipis" : "Jarak: lebar"));
            susunGambar();
        });
        bilahPembaca.addView(jarak, new LinearLayout.LayoutParams(dp(122), dp(44)));

        Button penuh = tombol("⛶");
        penuh.setTextSize(22);
        penuh.setOnClickListener(v -> setLayarPenuh(true));
        LinearLayout.LayoutParams pPenuh = new LinearLayout.LayoutParams(dp(50), dp(44));
        pPenuh.setMargins(dp(6), 0, 0, 0);
        bilahPembaca.addView(penuh, pPenuh);
        akar.addView(bilahPembaca);

        daftarPembaca = new RecyclerView(this);
        LinearLayoutManager pengelola = new LinearLayoutManager(this);
        pengelola.setRecycleChildrenOnDetach(true);
        daftarPembaca.setLayoutManager(pengelola);
        daftarPembaca.setBackgroundColor(Color.BLACK);
        daftarPembaca.setItemViewCacheSize(2);
        daftarPembaca.setItemAnimator(null);
        daftarPembaca.setOverScrollMode(View.OVER_SCROLL_NEVER);
        readerAdapter = new ReaderAdapter();
        daftarPembaca.setAdapter(readerAdapter);
        akar.addView(daftarPembaca, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(akar);
        terapkanInsetSistem(akar, true);
        susunGambar();
    }

    private void susunGambar() {
        if (readerAdapter != null) readerAdapter.notifyDataSetChanged();
    }

    private void terapkanInsetSistem(View view, boolean tambahRuangAtas) {
        int kiri = view.getPaddingLeft();
        int atas = view.getPaddingTop();
        int kanan = view.getPaddingRight();
        int bawah = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bilah = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(kiri, atas + bilah.top + (tambahRuangAtas ? dp(8) : 0),
                    kanan, bawah + bilah.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Menjaga rasio asli gambar saat lebarnya mengikuti layar. */
    private static class NaturalImageView extends AppCompatImageView {
        NaturalImageView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Drawable drawable = getDrawable();
            int lebar = MeasureSpec.getSize(widthMeasureSpec);
            if (drawable != null && drawable.getIntrinsicWidth() > 0
                    && drawable.getIntrinsicHeight() > 0 && lebar > 0) {
                int tinggi = Math.max(1, Math.round(
                        lebar * (drawable.getIntrinsicHeight() / (float) drawable.getIntrinsicWidth())));
                setMeasuredDimension(lebar, tinggi);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    private void setLayarPenuh(boolean aktif) {
        layarPenuh = aktif;
        if (bilahPembaca != null) bilahPembaca.setVisibility(aktif ? View.GONE : View.VISIBLE);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (aktif) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            window.getDecorView().setSystemUiVisibility(aktif
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    public void onBackPressed() {
        if (sedangMembaca) {
            if (layarPenuh) setLayarPenuh(false);
            else tampilkanGaleri();
        } else {
            super.onBackPressed();
        }
    }

    private TextView teks(String isi, int ukuran, int warna) {
        TextView view = new TextView(this);
        view.setText(isi);
        view.setTextSize(ukuran);
        view.setTextColor(warna);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button tombol(String isi) {
        Button button = new Button(this);
        button.setText(isi);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        return button;
    }

    private Button tombolTerang(String isi) {
        Button button = tombol(isi);
        button.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        return button;
    }

    private int dp(int nilai) {
        return Math.round(nilai * getResources().getDisplayMetrics().density);
    }

    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(MainActivity.this);
            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(128));
            rootLp.setMargins(dp(2), dp(2), dp(2), dp(2));
            frame.setLayoutParams(rootLp);

            ImageView image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView cek = teks("✓", 17, Color.WHITE);
            cek.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.BLACK);
            bg.setShape(GradientDrawable.OVAL);
            cek.setBackground(bg);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
            cp.setMargins(0, dp(8), dp(8), 0);
            frame.addView(cek, cp);
            return new Holder(frame, image, cek);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Uri uri = semuaFoto.get(position);
            Glide.with(MainActivity.this)
                    .load(uri)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .centerCrop()
                    .into(holder.image);
            holder.cek.setVisibility(pilihan.contains(uri) ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                ubahPilihan(uri);
                int posisi = holder.getBindingAdapterPosition();
                if (posisi != RecyclerView.NO_POSITION) notifyItemChanged(posisi);
            });
        }

        @Override public int getItemCount() { return semuaFoto.size(); }

        @Override public void onViewRecycled(@NonNull Holder holder) {
            Glide.with(MainActivity.this).clear(holder.image);
            holder.image.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView cek;
            Holder(View item, ImageView image, TextView cek) {
                super(item);
                this.image = image;
                this.cek = cek;
            }
        }
    }

    private class ReaderAdapter extends RecyclerView.Adapter<ReaderAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            NaturalImageView gambar = new NaturalImageView(MainActivity.this);
            gambar.setAdjustViewBounds(true);
            gambar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            gambar.setBackgroundColor(Color.BLACK);
            gambar.setOnClickListener(v -> setLayarPenuh(!layarPenuh));
            gambar.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(gambar);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.gambar.getLayoutParams();
            lp.setMargins(0, position > 0 ? dp(jarakDp) : 0, 0, 0);
            holder.gambar.setLayoutParams(lp);
            Glide.with(MainActivity.this)
                    .load(urutanTerpilih.get(position))
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(getResources().getDisplayMetrics().widthPixels, Target.SIZE_ORIGINAL)
                    .fitCenter()
                    .into(holder.gambar);
        }

        @Override public int getItemCount() { return urutanTerpilih.size(); }

        @Override public void onViewRecycled(@NonNull Holder holder) {
            Glide.with(MainActivity.this).clear(holder.gambar);
            holder.gambar.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        class Holder extends RecyclerView.ViewHolder {
            final NaturalImageView gambar;
            Holder(NaturalImageView gambar) {
                super(gambar);
                this.gambar = gambar;
            }
        }
    }

    private class ReorderAdapter extends RecyclerView.Adapter<ReorderAdapter.Holder> {
        private final List<Uri> data;
        ReorderAdapter(List<Uri> data) { this.data = data; }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(6), dp(12), dp(6));
            ImageView image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(image, new LinearLayout.LayoutParams(dp(72), dp(72)));
            TextView nomor = teks("", 16, Color.BLACK);
            nomor.setPadding(dp(16), 0, 0, 0);
            row.addView(nomor, new LinearLayout.LayoutParams(0, dp(72), 1));
            TextView handle = teks("↕", 28, Color.BLACK);
            handle.setGravity(Gravity.CENTER);
            row.addView(handle, new LinearLayout.LayoutParams(dp(52), dp(72)));
            return new Holder(row, image, nomor);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Glide.with(MainActivity.this).load(data.get(position)).centerCrop().into(holder.image);
            holder.nomor.setText("Foto " + (position + 1));
        }

        @Override public int getItemCount() { return data.size(); }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView nomor;
            Holder(View item, ImageView image, TextView nomor) {
                super(item);
                this.image = image;
                this.nomor = nomor;
            }
        }
    }
}
