package com.torch.converter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;




import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TorchConverter";
    
    private Button selectRomButton;
    private Button selectConfigButton;
    private Button selectOutputButton;
    private Button convertButton;
    private TextView statusText;
    private TextView configStatusText;
    private TextView outputStatusText;
    private TextView progressText;
    private ProgressBar progressBar;
    private RadioGroup configRadioGroup;
    private ImageView torchIcon;
    
    private Uri selectedRomUri;
    private Uri selectedConfigUri;
    private Uri selectedOutputUri;
    private String romFilePath;
    private String configDirPath;
    private String outputDirPath;
    private String selectedConfigType = "starship"; // Default to starship
    
    // Native method declarations
    public native String convertRomToO2R(String romPath, String outputPath, String configPath);
    
    // Callback method called from native code to update progress
    public void updateProgress(String message) {
        runOnUiThread(() -> {
            progressText.setText(message);
            Log.i(TAG, "Progress: " + message);
        });
    }
    
    static {
        System.loadLibrary("torch");
    }
    
    private final ActivityResultLauncher<Intent> romPickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                selectedRomUri = result.getData().getData();
                if (selectedRomUri != null) {
                    handleSelectedRom();
                }
            }
        });
    
    private final ActivityResultLauncher<Intent> configPickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                selectedConfigUri = result.getData().getData();
                if (selectedConfigUri != null) {
                    handleSelectedConfig();
                }
            }
        });
    
    private final ActivityResultLauncher<Intent> outputPickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                selectedOutputUri = result.getData().getData();
                if (selectedOutputUri != null) {
                    handleSelectedOutput();
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Customize action bar with centered title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowCustomEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setCustomView(R.layout.custom_action_bar);
        }
        
        initViews();
        setupClickListeners();
    }
    
    private void initViews() {
        selectRomButton = findViewById(R.id.selectRomButton);
        selectConfigButton = findViewById(R.id.selectConfigButton);
        selectOutputButton = findViewById(R.id.selectOutputButton);
        convertButton = findViewById(R.id.convertButton);
        statusText = findViewById(R.id.statusText);
        configStatusText = findViewById(R.id.configStatusText);
        outputStatusText = findViewById(R.id.outputStatusText);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
        configRadioGroup = findViewById(R.id.configRadioGroup);
        torchIcon = findViewById(R.id.torchIcon);
        
        convertButton.setEnabled(false);
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        
        // Set up radio group listener
        configRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.starshipRadio) {
                selectedConfigType = "starship";
                Log.i(TAG, "Selected configuration: Starship (Star Fox 64)");
            } else if (checkedId == R.id.spaghettiRadio) {
                selectedConfigType = "spaghetti";
                Log.i(TAG, "Selected configuration: Spaghetti Kart (Mario Kart 64)");
            }
            // Reset config selection when changing configuration type
            selectedConfigUri = null;
            configDirPath = null;
            configStatusText.setText("Config: Not selected");
            // Keep output directory selection when changing config type
            updateConvertButtonState();
        });
    }
    
    private void setupClickListeners() {
        selectRomButton.setOnClickListener(v -> openRomPicker());
        selectConfigButton.setOnClickListener(v -> openConfigPicker());
        selectOutputButton.setOnClickListener(v -> openOutputPicker());
        convertButton.setOnClickListener(v -> convertRom());
    }
    
    private void openRomPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Accept all file types but we'll validate .v64 extension
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "*/*"});
        romPickerLauncher.launch(intent);
    }
    
    private void openConfigPicker() {
        // Load the selected configuration (starship or spaghetti)
        handleSelectedConfig();
    }
    
    private void openOutputPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        outputPickerLauncher.launch(intent);
    }
    
    private void handleSelectedRom() {
        try {
            String fileName = getFileName(selectedRomUri);
            
            // Validate .z64 extension
            if (!fileName.toLowerCase().endsWith(".z64")) {
                statusText.setText("Error: Only .z64 format ROMs are supported");
                String gameType = selectedConfigType.equals("starship") ? "Star Fox 64" : "Mario Kart 64";
                Toast.makeText(this, "Please select a .z64 format " + gameType + " ROM", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Copy ROM to internal storage for processing
            romFilePath = copyRomToInternalStorage();
            
            statusText.setText("ROM: " + fileName);
            updateConvertButtonState();
            
        } catch (IOException e) {
            Log.e(TAG, "Error handling selected ROM", e);
            Toast.makeText(this, "Error reading ROM file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String copyRomToInternalStorage() throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(selectedRomUri);
        if (inputStream == null) {
            throw new IOException("Cannot open ROM file");
        }
        
        // Put ROM in the app root directory alongside config files
        File appRootDir = getExternalFilesDir(null);
        
        String fileName = getFileName(selectedRomUri);
        // Use a standard name for the ROM file
        File romFile = new File(appRootDir, "baserom.z64");
        
        FileOutputStream outputStream = new FileOutputStream(romFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        inputStream.close();
        outputStream.close();
        
        Log.i(TAG, "ROM copied to app root: " + romFile.getAbsolutePath());
        return romFile.getAbsolutePath();
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    private void handleSelectedConfig() {
        try {
            // Copy bundled assets to internal storage based on selected configuration
            configDirPath = copyBundledAssetsToInternalStorage();
            
            String configName = selectedConfigType.equals("starship") ? 
                "Starship (Star Fox 64)" : "Spaghetti Kart (Mario Kart 64)";
            configStatusText.setText("Config: " + configName + " assets loaded");
            updateConvertButtonState();
            
        } catch (IOException e) {
            Log.e(TAG, "Error loading bundled config", e);
            Toast.makeText(this, "Error loading bundled config", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleSelectedOutput() {
        try {
            // Get the selected directory path for display
            String displayPath = getDisplayPathFromUri(selectedOutputUri);
            outputDirPath = getWritablePathFromUri(selectedOutputUri);
            
            outputStatusText.setText("Output: " + displayPath);
            updateConvertButtonState();
            
            Log.i(TAG, "Output directory selected: " + displayPath);
            Log.i(TAG, "Writable path: " + outputDirPath);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling selected output directory", e);
            Toast.makeText(this, "Error accessing selected directory", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getDisplayPathFromUri(Uri uri) {
        // Extract a user-friendly display path from the URI
        String path = uri.toString();
        if (path.contains("primary:")) {
            String folder = path.substring(path.lastIndexOf("primary:") + 8);
            return "Internal Storage/" + folder.replace("%2F", "/");
        } else if (path.contains("home:")) {
            String folder = path.substring(path.lastIndexOf("home:") + 5);
            return "Documents/" + folder.replace("%2F", "/");
        } else {
            return "Selected Directory";
        }
    }
    
    private String getWritablePathFromUri(Uri uri) {
        // For now, we'll still copy to app directory but remember the user's choice
        // In the future, we could use DocumentFile to write directly to the selected location
        return getExternalFilesDir(null).getAbsolutePath();
    }
    

    
    private void copyFileToSelectedDirectory(File sourceFile, String fileName) throws IOException {
        if (selectedOutputUri == null) return;
        
        try {
            // Use DocumentFile to create and write to the selected directory
            androidx.documentfile.provider.DocumentFile outputDir = 
                androidx.documentfile.provider.DocumentFile.fromTreeUri(this, selectedOutputUri);
            
            if (outputDir == null || !outputDir.canWrite()) {
                throw new IOException("Cannot write to selected directory");
            }
            
            // Delete existing file if it exists
            androidx.documentfile.provider.DocumentFile existingFile = outputDir.findFile(fileName);
            if (existingFile != null) {
                existingFile.delete();
            }
            
            // Create new file
            androidx.documentfile.provider.DocumentFile newFile = 
                outputDir.createFile("application/octet-stream", fileName);
            
            if (newFile == null) {
                throw new IOException("Failed to create file in selected directory");
            }
            
            // Copy file content
            try (java.io.FileInputStream inputStream = new java.io.FileInputStream(sourceFile);
                 java.io.OutputStream outputStream = getContentResolver().openOutputStream(newFile.getUri())) {
                
                if (outputStream == null) {
                    throw new IOException("Cannot open output stream");
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            Log.i(TAG, "File copied to selected directory: " + fileName);
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying file to selected directory", e);
            throw new IOException("Failed to copy file: " + e.getMessage());
        }
    }
    
    private String copyBundledAssetsToInternalStorage() throws IOException {
        // Use the app's external files directory as the root (not a subfolder)
        File appRootDir = getExternalFilesDir(null);
        
        // Clean up any existing config files
        File existingConfig = new File(appRootDir, "config.yml");
        if (existingConfig.exists()) {
            existingConfig.delete();
        }
        File existingAssets = new File(appRootDir, "assets");
        if (existingAssets.exists()) {
            deleteRecursively(existingAssets);
        }
        File existingYamls = new File(appRootDir, "yamls");
        if (existingYamls.exists()) {
            deleteRecursively(existingYamls);
        }
        File existingInclude = new File(appRootDir, "include");
        if (existingInclude.exists()) {
            deleteRecursively(existingInclude);
        }
        
        // Copy bundled assets based on selected configuration
        copyAssetDirectory(selectedConfigType, appRootDir);
        
        Log.i(TAG, "Config copied to app root: " + appRootDir.getAbsolutePath());
        
        // Don't create torch.hash.yml - let Torch generate it automatically
        
        // Debug: List the structure that was created
        listDirectoryContents(appRootDir, "");
        
        return appRootDir.getAbsolutePath();
    }
    
    private void listDirectoryContents(File dir, String indent) {
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    Log.i(TAG, indent + "📁 " + file.getName() + "/");
                    listDirectoryContents(file, indent + "  ");
                } else {
                    Log.i(TAG, indent + "📄 " + file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }
    }
    
    private void copyAssetDirectory(String assetPath, File destDir) throws IOException {
        String[] assets = getAssets().list(assetPath);
        if (assets == null) return;
        
        for (String asset : assets) {
            String fullAssetPath = assetPath + "/" + asset;
            File destFile = new File(destDir, asset);
            
            try {
                // Try to list contents - if it succeeds, it's a directory
                String[] subAssets = getAssets().list(fullAssetPath);
                if (subAssets != null && subAssets.length > 0) {
                    // It's a directory
                    destFile.mkdirs();
                    copyAssetDirectory(fullAssetPath, destFile);
                } else {
                    // It's a file
                    copyAssetFile(fullAssetPath, destFile);
                }
            } catch (IOException e) {
                // If listing fails, assume it's a file
                copyAssetFile(fullAssetPath, destFile);
            }
        }
        
        Log.i(TAG, "Copied asset directory: " + assetPath + " -> " + destDir.getAbsolutePath());
    }
    
    private void copyAssetFile(String assetPath, File destFile) throws IOException {
        try (InputStream inputStream = getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            Log.i(TAG, "Copied asset: " + assetPath + " -> " + destFile.getName());
        }
    }
    
    private void copyDirectoryFromUri(Uri treeUri, File destDir) throws IOException {
        Log.i(TAG, "Copying config directory from: " + treeUri.toString());
        
        try {
            // Use DocumentsContract to traverse the directory tree
            android.database.Cursor cursor = getContentResolver().query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    
                    Log.i(TAG, "Found: " + displayName + " (type: " + mimeType + ")");
                    
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        // It's a directory - create it and recurse
                        File subDir = new File(destDir, displayName);
                        subDir.mkdirs();
                        copyDirectoryFromUri(documentUri, subDir);
                    } else {
                        // It's a file - copy it
                        copyFileFromUri(documentUri, new File(destDir, displayName));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying directory", e);
            throw new IOException("Failed to copy config directory: " + e.getMessage());
        }
    }
    
    private void copyFileFromUri(Uri fileUri, File destFile) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {
            
            if (inputStream == null) {
                throw new IOException("Cannot open file: " + fileUri);
            }
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            Log.i(TAG, "Copied file: " + destFile.getName());
        }
    }
    
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    private void updateConvertButtonState() {
        // Convert button is enabled when ROM and config are selected
        // Output directory is optional (defaults to app directory)
        convertButton.setEnabled(romFilePath != null && configDirPath != null);
    }
    
    private void convertRom() {
        if (romFilePath == null) {
            Toast.makeText(this, "Please select a ROM first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (configDirPath == null) {
            Toast.makeText(this, "Please select config directory first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress and start torch animation
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        convertButton.setEnabled(false);
        statusText.setText("Converting ROM to O2R...");
        progressText.setText("Initializing Torch...");
        
        // Start the cool torch animation! 🔥
        startTorchAnimation();
        
        // Run conversion in background thread with timeout
        new Thread(() -> {
            try {
                // Use selected output directory or default to app directory
                File outputDir = outputDirPath != null ? new File(outputDirPath) : getExternalFilesDir(null);
                // Use appropriate filename based on selected configuration
                String outputFileName = selectedConfigType.equals("starship") ? "sf64.o2r" : "mk64.o2r";
                String outputPath = new File(outputDir, outputFileName).getAbsolutePath();
                Log.i(TAG, "ROM path: " + romFilePath);
                Log.i(TAG, "Output path: " + outputPath);
                Log.i(TAG, "Config path: " + configDirPath);
                
                // Check if files exist before conversion
                File romFile = new File(romFilePath);
                File configDir = new File(configDirPath);
                Log.i(TAG, "ROM file exists: " + romFile.exists() + " (size: " + romFile.length() + " bytes)");
                Log.i(TAG, "Config dir exists: " + configDir.exists());
                
                if (configDir.exists()) {
                    File configYml = new File(configDir, "config.yml");
                    Log.i(TAG, "config.yml exists: " + configYml.exists());
                }
                
                // Update progress during conversion
                runOnUiThread(() -> progressText.setText("Reading ROM file..."));
                Thread.sleep(500); // Brief pause to show progress
                
                runOnUiThread(() -> progressText.setText("Loading configuration..."));
                Thread.sleep(500);
                
                runOnUiThread(() -> progressText.setText("Processing assets..."));
                Log.i(TAG, "Starting native conversion...");
                String result = convertRomToO2R(romFilePath, outputPath, configDirPath);
                Log.i(TAG, "Native conversion returned: " + result);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    convertButton.setEnabled(true);
                    
                    // Stop the torch animation
                    stopTorchAnimation();
                    
                    if (result != null && result.equals("success")) {
                        // Double-check if the file was actually created
                        File outputFile = new File(outputPath);
                        if (outputFile.exists() && outputFile.length() > 0) {
                            String configName = selectedConfigType.equals("starship") ? "Starship" : "Spaghetti Kart";
                            
                            // If user selected a custom output directory, copy the file there
                            if (selectedOutputUri != null) {
                                try {
                                    copyFileToSelectedDirectory(outputFile, outputFileName);
                                    String displayPath = getDisplayPathFromUri(selectedOutputUri);
                                    statusText.setText("Conversion complete! " + outputFileName + " saved to " + displayPath + " (" + (outputFile.length() / 1024) + " KB)");
                                    Toast.makeText(this, outputFileName + " created successfully for " + configName + "!\nSaved to: " + displayPath, Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to copy to selected directory", e);
                                    statusText.setText("Conversion complete! " + outputFileName + " saved to app directory (" + (outputFile.length() / 1024) + " KB)");
                                    Toast.makeText(this, outputFileName + " created successfully for " + configName + "!\nSaved to app directory (failed to copy to selected location)", Toast.LENGTH_LONG).show();
                                }
                            } else {
                                statusText.setText("Conversion complete! " + outputFileName + " saved to app directory (" + (outputFile.length() / 1024) + " KB)");
                                Toast.makeText(this, outputFileName + " created successfully for " + configName + "!\nLocation: " + outputFile.getParent(), Toast.LENGTH_LONG).show();
                            }
                            
                            Log.i(TAG, "Output file confirmed: " + outputFile.getAbsolutePath() + " (" + outputFile.length() + " bytes)");
                        } else {
                            statusText.setText("Conversion reported success but no output file found");
                            Toast.makeText(this, "No output file created", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "No output file found at: " + outputPath);
                        }
                    } else {
                        statusText.setText("Conversion failed: " + (result != null ? result : "Unknown error"));
                        Toast.makeText(this, "Conversion failed", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Conversion failed with result: " + result);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Conversion error", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressText.setVisibility(View.GONE);
                    convertButton.setEnabled(true);
                    statusText.setText("Conversion failed: " + e.getMessage());
                    Toast.makeText(this, "Conversion failed", Toast.LENGTH_SHORT).show();
                    
                    // Stop the torch animation on error
                    stopTorchAnimation();
                });
            }
        }).start();
    }

    private void startTorchAnimation() {
        runOnUiThread(() -> {
            // Start the fade in/out animation
            torchIcon.setImageResource(R.mipmap.ic_launcher);
            Animation animation = AnimationUtils.loadAnimation(this, R.anim.torch_dim_bright);
            torchIcon.startAnimation(animation);
            Log.i(TAG, "🔥 Torch fade animation started!");
        });
    }

    private void stopTorchAnimation() {
        runOnUiThread(() -> {
            // Stop the fade animation and show static icon
            torchIcon.clearAnimation();
            torchIcon.setImageResource(R.mipmap.ic_launcher);
            Log.i(TAG, "🔥 Torch fade animation stopped!");
        });
    }
}