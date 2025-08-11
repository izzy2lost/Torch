#include <jni.h>
#include <string>
#include <android/log.h>
#include <filesystem>
#include <fstream>
#include "Companion.h"
#include "factories/TextureFactory.h"
#include "factories/BlobFactory.h"
#include "factories/VtxFactory.h"
#include "factories/MtxFactory.h"
#include "factories/FloatFactory.h"
#include "factories/IncludeFactory.h"
#include "factories/DisplayListFactory.h"
#include "factories/LightsFactory.h"
#include "factories/Vec3fFactory.h"
#include "factories/Vec3sFactory.h"
#include "factories/GenericArrayFactory.h"
#include "factories/AssetArrayFactory.h"
#include "factories/ViewportFactory.h"
#include "factories/CompressedTextureFactory.h"

#define LOG_TAG "TorchJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Define the static member variable
Companion* Companion::Instance = nullptr;

// Custom factory registration function since RegisterFactory is private
void RegisterFactoriesManually(Companion* companion) {
    // We can't access RegisterFactory directly since it's private
    // But we can use reflection or friend functions, or modify the Companion class
    // For now, let's try calling Init() which registers factories, then Process() separately
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_torch_converter_MainActivity_convertRomToO2R(JNIEnv *env, jobject thiz, 
                                                       jstring romPath, jstring outputPath, jstring configPath) {
    
    const char *romPathStr = env->GetStringUTFChars(romPath, nullptr);
    const char *outputPathStr = env->GetStringUTFChars(outputPath, nullptr);
    const char *configPathStr = env->GetStringUTFChars(configPath, nullptr);
    
    LOGI("Starting ROM conversion: %s -> %s", romPathStr, outputPathStr);
    
    try {
        // Check if ROM file exists
        if (!std::filesystem::exists(romPathStr)) {
            LOGE("ROM file does not exist: %s", romPathStr);
            env->ReleaseStringUTFChars(romPath, romPathStr);
            env->ReleaseStringUTFChars(outputPath, outputPathStr);
            return env->NewStringUTF("ROM file not found");
        }
        
        // Create output directory if it doesn't exist
        std::filesystem::path outputDir = std::filesystem::path(outputPathStr).parent_path();
        if (!std::filesystem::exists(outputDir)) {
            std::filesystem::create_directories(outputDir);
        }
        
        // Initialize Companion with O2R mode and config directory
        std::filesystem::path romFilePath(romPathStr);
        std::string configDir(configPathStr);
        
        LOGI("Config directory: %s", configDir.c_str());
        
        // List contents of config directory for debugging
        try {
            LOGI("Contents of config directory:");
            for (const auto& entry : std::filesystem::directory_iterator(configDir)) {
                LOGI("  %s", entry.path().filename().string().c_str());
            }
        } catch (const std::exception& e) {
            LOGE("Error listing config directory: %s", e.what());
        }
        
        // Check if config.yml exists
        std::string configFile = configDir + "/config.yml";
        if (!std::filesystem::exists(configFile)) {
            LOGE("config.yml not found at: %s", configFile.c_str());
            env->ReleaseStringUTFChars(romPath, romPathStr);
            env->ReleaseStringUTFChars(outputPath, outputPathStr);
            env->ReleaseStringUTFChars(configPath, configPathStr);
            return env->NewStringUTF("config.yml not found in selected folder");
        }
        
        // Check if assets directory exists
        std::string assetsDir = configDir + "/assets";
        if (!std::filesystem::exists(assetsDir)) {
            LOGE("assets directory not found at: %s", assetsDir.c_str());
            env->ReleaseStringUTFChars(romPath, romPathStr);
            env->ReleaseStringUTFChars(outputPath, outputPathStr);
            env->ReleaseStringUTFChars(configPath, configPathStr);
            return env->NewStringUTF("assets directory not found in selected folder");
        }
        
        LOGI("Found config.yml and assets directory");
        
        // Check if output directory exists
        std::filesystem::path outputFilePath(outputPathStr);
        std::filesystem::path outputDirPath = outputFilePath.parent_path();
        if (!std::filesystem::exists(outputDirPath)) {
            LOGI("Creating output directory: %s", outputDirPath.string().c_str());
            std::filesystem::create_directories(outputDirPath);
        }
        
        // Update progress
        jclass clazz = env->GetObjectClass(thiz);
        jmethodID updateProgressMethod = env->GetMethodID(clazz, "updateProgress", "(Ljava/lang/String;)V");
        
        auto updateProgress = [&](const char* message) {
            jstring jmsg = env->NewStringUTF(message);
            env->CallVoidMethod(thiz, updateProgressMethod, jmsg);
            env->DeleteLocalRef(jmsg);
        };
        
        updateProgress("Analyzing ROM file...");
        LOGI("Creating Companion instance...");
        LOGI("ROM file path: %s", romFilePath.string().c_str());
        LOGI("Config directory (working dir): %s", configDir.c_str());
        LOGI("Output path: %s", outputPathStr);
        
        // Declare variables at broader scope
        bool isCompressed = false;
        std::string romVersion = "Unknown";
        
        // Check ROM file and calculate SHA1 hash
        if (std::filesystem::exists(romFilePath)) {
            auto romSize = std::filesystem::file_size(romFilePath);
            LOGI("ROM file size: %zu bytes", romSize);
            
            // Calculate SHA1 hash of the ROM
            std::ifstream romFile(romFilePath, std::ios::binary);
            if (romFile.is_open()) {
                // Read entire file for hash calculation
                std::vector<uint8_t> romData((std::istreambuf_iterator<char>(romFile)),
                                           std::istreambuf_iterator<char>());
                romFile.close();
                
                // Calculate SHA1 hash using Torch's method
                std::string romHash = Companion::CalculateHash(romData);
                LOGI("ROM SHA1 hash: %s", romHash.c_str());
                
                // Check if this hash is in the Starship config
                // Both compressed and decompressed versions are supported
                
                // US 1.1 (decompressed)
                if (romHash == "f7475fb11e7e6830f82883412638e8390791ab87") {
                    romVersion = "Star Fox 64 (U) (V1.1) - Decompressed";
                    isCompressed = false;
                // US 1.1 (compressed)
                } else if (romHash == "09f0d105f476b00efa5303a3ebc42e60a7753b7a") {
                    romVersion = "Star Fox 64 (U) (V1.1) - Compressed";
                    isCompressed = true;
                // US 1.0 (decompressed)
                } else if (romHash == "63b69f0ef36306257481afc250f9bc304c7162b2") {
                    romVersion = "Star Fox 64 (U) (V1.0) - Decompressed";
                    isCompressed = false;
                // US 1.0 (compressed)
                } else if (romHash == "d8b1088520f7c5f81433292a9258c1184afa1457") {
                    romVersion = "Star Fox 64 (U) (V1.0) - Compressed";
                    isCompressed = true;
                // JP 1.0 (decompressed)
                } else if (romHash == "d064229a32cc05ab85e2381ce07744eb3ffaf530") {
                    romVersion = "Star Fox 64 (JP) (V1.0) - Decompressed";
                    isCompressed = false;
                // JP 1.0 (compressed)
                } else if (romHash == "9bd71afbecf4d0a43146e4e7a893395e19bf3220") {
                    romVersion = "Star Fox 64 (JP) (V1.0) - Compressed";
                    isCompressed = true;
                // EU 1.0 (decompressed)
                } else if (romHash == "09f5d5c14219fc77a36c5a6ad5e63f7abd8b3385") {
                    romVersion = "Star Fox 64 (EU) (V1.0) - Decompressed";
                    isCompressed = false;
                // EU 1.0 (compressed)
                } else if (romHash == "05b307b8804f992af1a1e2fbafbd588501fdf799") {
                    romVersion = "Star Fox 64 (EU) (V1.0) - Compressed";
                    isCompressed = true;
                // CN 1.1 (decompressed)
                } else if (romHash == "3a05aba5549fa71e8b16a0c6e2c8481b070818a9") {
                    romVersion = "Star Fox 64 (CN) (V1.1) - Decompressed";
                    isCompressed = false;
                // CN 1.1 (compressed)
                } else if (romHash == "c8a10699dea52f4bb2e2311935c1376dfb352e7a") {
                    romVersion = "Star Fox 64 (CN) (V1.1) - Compressed";
                    isCompressed = true;
                // Mario Kart 64 (US)
                } else if (romHash == "579c48e211ae952530ffc8738709f078d5dd215e") {
                    romVersion = "Mario Kart 64 (US)";
                    isCompressed = false;
                } else {
                    LOGE("Unknown ROM hash: %s - This ROM may not be supported", romHash.c_str());
                    LOGE("Supported versions:");
                    LOGE("  Star Fox 64: US 1.0/1.1, JP 1.0, EU 1.0, CN 1.1 (compressed or decompressed)");
                    LOGE("  Mario Kart 64: US (decompressed)");
                }
                
                LOGI("Detected: %s", romVersion.c_str());
                if (isCompressed) {
                    LOGI("⚠️  This ROM is compressed and will need to be decompressed during processing");
                    LOGI("⚠️  This may take a couple of minutes on mobile devices");
                    LOGI("⚠️  For faster processing, use a pre-decompressed ROM");
                }
            } else {
                LOGE("Cannot open ROM file for reading");
            }
        } else {
            LOGE("ROM file does not exist at: %s", romFilePath.string().c_str());
        }
        
        updateProgress("Setting up working directory...");
        
        // Change working directory to where config files are located
        std::filesystem::current_path(configDir);
        LOGI("Changed working directory to: %s", std::filesystem::current_path().string().c_str());
        
        // Check if we can write to the working directory
        std::string testFile = configDir + "/test_write.tmp";
        try {
            std::ofstream test(testFile);
            if (test.is_open()) {
                test << "test";
                test.close();
                std::filesystem::remove(testFile);
                LOGI("Working directory is writable");
            } else {
                LOGE("Cannot write to working directory");
            }
        } catch (const std::exception& e) {
            LOGE("Write test failed: %s", e.what());
        }
        
        updateProgress("Creating Torch instance...");
        
        // Use the same directory as ROM and config files for output
        std::string outputDirectory = configDir;
        
        LOGI("Creating Companion with:");
        LOGI("  ROM path: %s", romFilePath.string().c_str());
        LOGI("  Archive type: O2R");
        LOGI("  Debug: false");
        LOGI("  Modding: false");
        LOGI("  Source dir: %s", configDir.c_str());
        LOGI("  Dest dir: %s", outputDirectory.c_str());
        
        // Use the correct constructor signature
        LOGI("Creating Companion with correct constructor...");
        
        // Add more detailed logging before constructor
        LOGI("Final check before Companion creation:");
        LOGI("  ROM file path: %s", romFilePath.string().c_str());
        LOGI("  ROM file exists: %s", std::filesystem::exists(romFilePath) ? "YES" : "NO");
        if (std::filesystem::exists(romFilePath)) {
            LOGI("  ROM file size: %zu bytes", std::filesystem::file_size(romFilePath));
            LOGI("  ROM file is regular file: %s", std::filesystem::is_regular_file(romFilePath) ? "YES" : "NO");
        }
        LOGI("  Config dir: %s", configDir.c_str());
        LOGI("  Output dir: %s", outputDirectory.c_str());
        
        // Try loading ROM data manually first (for logging/verification)
        LOGI("Attempting to load ROM data manually...");
        std::vector<uint8_t> romData;
        try {
            std::ifstream romFile(romFilePath, std::ios::binary);
            if (!romFile.is_open()) {
                LOGE("Failed to open ROM file for reading");
                env->ReleaseStringUTFChars(romPath, romPathStr);
                env->ReleaseStringUTFChars(outputPath, outputPathStr);
                env->ReleaseStringUTFChars(configPath, configPathStr);
                return env->NewStringUTF("Cannot open ROM file");
            }
            romFile.seekg(0, std::ios::end);
            size_t fileSize = romFile.tellg();
            romFile.seekg(0, std::ios::beg);
            romData.resize(fileSize);
            romFile.read(reinterpret_cast<char*>(romData.data()), fileSize);
            romFile.close();
            LOGI("ROM data loaded successfully: %zu bytes", romData.size());
            if (romData.size() >= 16) {
                LOGI("ROM data first 16 bytes: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
                     romData[0], romData[1], romData[2], romData[3], romData[4], romData[5], romData[6], romData[7],
                     romData[8], romData[9], romData[10], romData[11], romData[12], romData[13], romData[14], romData[15]);
            }
        } catch (const std::exception& e) {
            LOGE("Exception loading ROM data for logging: %s", e.what());
        }

        // Create Companion with absolute ROM path and proper directories
        Companion* companion = nullptr;
        try {
            LOGI("Using vector constructor since filesystem path failed");
            companion = new Companion(romData, ArchiveType::O2R, false, false, configDir, outputDirectory);
            
            LOGI("Companion constructor completed");
            
            // Check ROM hash immediately after construction
            if (companion != nullptr) {
                try {
                    auto& companionRomData = companion->GetRomData();
                    std::string companionHash = Companion::CalculateHash(companionRomData);
                    LOGI("ROM hash from Companion: %s", companionHash.c_str());
                    LOGI("ROM data size from Companion: %zu bytes", companionRomData.size());
                    
                    // Compare with our manually loaded ROM data
                    std::string manualHash = Companion::CalculateHash(romData);
                    LOGI("ROM hash from manual load: %s", manualHash.c_str());
                    LOGI("ROM data size from manual load: %zu bytes", romData.size());
                    
                    if (companionHash == manualHash) {
                        LOGI("✓ ROM hashes match - data is consistent");
                    } else {
                        LOGE("✗ ROM hashes DON'T match - Companion has different data!");
                        LOGE("This suggests Companion is reading from a different file or getting corrupted data");
                        
                        // Compare first few bytes
                        if (companionRomData.size() >= 16 && romData.size() >= 16) {
                            LOGI("Companion ROM first 16 bytes: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
                                 companionRomData[0], companionRomData[1], companionRomData[2], companionRomData[3],
                                 companionRomData[4], companionRomData[5], companionRomData[6], companionRomData[7],
                                 companionRomData[8], companionRomData[9], companionRomData[10], companionRomData[11],
                                 companionRomData[12], companionRomData[13], companionRomData[14], companionRomData[15]);
                            
                            LOGI("Manual ROM first 16 bytes:    %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
                                 romData[0], romData[1], romData[2], romData[3],
                                 romData[4], romData[5], romData[6], romData[7],
                                 romData[8], romData[9], romData[10], romData[11],
                                 romData[12], romData[13], romData[14], romData[15]);
                        }
                    }
                    
                } catch (const std::exception& e) {
                    LOGE("Exception checking ROM hash: %s", e.what());
                }
            }

            if (companion == nullptr) {
                LOGE("Failed to create Companion instance");
                env->ReleaseStringUTFChars(romPath, romPathStr);
                env->ReleaseStringUTFChars(outputPath, outputPathStr);
                env->ReleaseStringUTFChars(configPath, configPathStr);
                return env->NewStringUTF("Failed to create Companion instance");
            }

            // Set singleton (if used downstream)
            Companion::Instance = companion;

            // Initialize (this calls Companion::Process() internally)
            updateProgress("Processing ROM with Torch...");
            LOGI("About to try Torch processing...");
            LOGI("Trying Process() first, then Init() as fallback...");
            
            try {
                // Use our new method to register factories only, then call Process separately
                LOGI("Calling companion->InitFactoriesOnly() to register factories...");
                companion->InitFactoriesOnly(ExportType::Binary);
                LOGI("Factories registered successfully");
                
                // Check if cartridge is created properly before calling Process
                auto cartridge = companion->GetCartridge();
                LOGI("Cartridge before Process(): %p", cartridge);
                if (cartridge != nullptr) {
                    LOGI("Cartridge hash: %s", cartridge->GetHash().c_str());
                    LOGI("Cartridge title: %s", cartridge->GetGameTitle().c_str());
                } else {
                    LOGI("Cartridge is null - this is expected before Process()");
                }
                
                LOGI("Now calling companion->Process() to convert ROM...");
                if (isCompressed) {
                    updateProgress("Decompressing ROM - this may take a couple of minutes...");
                    LOGI("⏳ Starting ROM decompression - this is the slow part...");
                } else {
                    updateProgress("Processing ROM assets...");
                }
                
                // Set a longer timeout for the process
                companion->Process();
                LOGI("*** SUCCESS: Companion Process completed! ***");
                
                // Check if cartridge was created during Process
                cartridge = companion->GetCartridge();
                LOGI("Cartridge after Process(): %p", cartridge);
                if (cartridge != nullptr) {
                    LOGI("Final cartridge hash: %s", cartridge->GetHash().c_str());
                    LOGI("Final cartridge title: %s", cartridge->GetGameTitle().c_str());
                }
                
            } catch (const std::exception& e) {
                LOGE("Exception during Torch processing: %s", e.what());
                env->ReleaseStringUTFChars(romPath, romPathStr);
                env->ReleaseStringUTFChars(outputPath, outputPathStr);
                env->ReleaseStringUTFChars(configPath, configPathStr);
                return env->NewStringUTF(e.what());
            } catch (...) {
                LOGE("Unknown exception during Torch processing");
                env->ReleaseStringUTFChars(romPath, romPathStr);
                env->ReleaseStringUTFChars(outputPath, outputPathStr);
                env->ReleaseStringUTFChars(configPath, configPathStr);
                return env->NewStringUTF("Unknown exception during Torch processing");
            }

            // Verify cartridge after Init
            auto cartridge = companion->GetCartridge();
            LOGI("Post-Init GetCartridge() returned: %p", cartridge);
            if (cartridge == nullptr) {
                LOGE("Cartridge is null after Init. Ensure ROM is supported and config has an entry for its SHA1.");
            }

            updateProgress("Generating O2R file...");

            // Check if torch.hash.yml was created
            std::string hashFile = configDir + "/torch.hash.yml";
            if (std::filesystem::exists(hashFile)) {
                LOGI("torch.hash.yml was created successfully");
            } else {
                LOGI("torch.hash.yml was not created (this might be normal)");
            }

            // Check output file
            if (std::filesystem::exists(outputPathStr)) {
                auto outSize = std::filesystem::file_size(outputPathStr);
                LOGI("O2R file created successfully: %s (size: %zu bytes)", outputPathStr, outSize);
            } else {
                LOGE("O2R file was not created at: %s", outputPathStr);
                delete companion;
                Companion::Instance = nullptr;
                env->ReleaseStringUTFChars(romPath, romPathStr);
                env->ReleaseStringUTFChars(outputPath, outputPathStr);
                env->ReleaseStringUTFChars(configPath, configPathStr);
                return env->NewStringUTF("O2R file was not created");
            }

        } catch (const std::exception& e) {
            LOGE("Exception during Torch processing: %s", e.what());
            if (companion) {
                delete companion;
                Companion::Instance = nullptr;
            }
            env->ReleaseStringUTFChars(romPath, romPathStr);
            env->ReleaseStringUTFChars(outputPath, outputPathStr);
            env->ReleaseStringUTFChars(configPath, configPathStr);
            return env->NewStringUTF(e.what());
        }

        LOGI("Torch processing completed successfully");
        LOGI("ROM conversion completed successfully");

        // Clean up
        delete companion;
        Companion::Instance = nullptr;

        env->ReleaseStringUTFChars(romPath, romPathStr);
        env->ReleaseStringUTFChars(outputPath, outputPathStr);
        env->ReleaseStringUTFChars(configPath, configPathStr);
        return env->NewStringUTF("success");
        
    } catch (const std::exception& e) {
        LOGE("Exception during conversion: %s", e.what());
        env->ReleaseStringUTFChars(romPath, romPathStr);
        env->ReleaseStringUTFChars(outputPath, outputPathStr);
        env->ReleaseStringUTFChars(configPath, configPathStr);
        return env->NewStringUTF(e.what());
    } catch (...) {
        LOGE("Unknown exception during conversion");
        env->ReleaseStringUTFChars(romPath, romPathStr);
        env->ReleaseStringUTFChars(outputPath, outputPathStr);
        env->ReleaseStringUTFChars(configPath, configPathStr);
        return env->NewStringUTF("Unknown error occurred");
    }
}