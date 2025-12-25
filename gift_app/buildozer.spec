[app]

# (str) Title of your application
title = Gift App

# (str) Package name
package.name = giftapp

# (str) Package domain (needed for android/ios packaging)
package.domain = org.test

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (let empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas,txt

# (list) List of inclusions using pattern matching
#source.include_patterns = assets/*,images/*.png

# (list) Source files to exclude (let empty to not exclude anything)
#source.exclude_exts = spec

# (list) List of directory to exclude (let empty to not exclude anything)
#source.exclude_dirs = tests, bin, venv

# (list) List of exclusions using pattern matching
#source.exclude_patterns = license,images/*/*.jpg

# (str) Application versioning (method 1)
version = 0.1

# (str) Application versioning (method 2)
# version.regex = __version__ = ['"](.*)['"]
# version.filename = %(source.dir)s/main.py

# (list) Application requirements
# comma separated e.g. requirements = sqlite3,kivy
requirements = python3,kivy

# (str) Custom source folders for requirements
# Sets custom source for any requirements with recipes
# requirements.source.kivy = ../../kivy

# (list) Garden requirements
#garden_requirements =

# (str) Presplash of the application
#presplash.filename = %(source.dir)s/data/presplash.png

# (str) Icon of the application
#icon.filename = %(source.dir)s/data/icon.png

# (str) Supported orientation (one of landscape, sensorLandscape, portrait or all)
orientation = portrait

# (list) List of service to declare
#services = NAME:ENTRYPOINT_TO_PY,NAME2:ENTRYPOINT2_TO_PY

#
# Android specific
#

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 1

# (string) Presplash background color (for android toolchain)
# Supported formats are: #RRGGBB #AARRGGBB or one of the following names:
# red, blue, green, black, white, gray, cyan, magenta, yellow, lightgray,
# darkgray, grey, lightgrey, darkgrey, aqua, fuchsia, lime, maroon, navy,
# olive, purple, silver, teal.
#android.presplash_color = #1EC5FA

# (string) Presplash animation using Lottie format.
# see https://lottiefiles.com/ for examples and https://airbnb.io/lottie/
#android.presplash_lottie = "path/to/lottie/file.json"

# (str) Adaptive icon of the application (used if Android API level is 26+ at least)
#icon.adaptive_foreground.filename = %(source.dir)s/data/icon_fg.png
#icon.adaptive_background.filename = %(source.dir)s/data/icon_bg.png

# (list) Permissions
android.permissions = INTERNET

# (int) Target Android API, should be as high as possible.
#android.api = 27

# (int) Minimum API your APK will support.
#android.minapi = 21

# (int) Android SDK version to use
#android.sdk = 20

# (str) Android NDK version to use
#android.ndk = 19b

# (int) Android NDK API to use. This is the minimum API your app will support, it should usually match android.minapi.
#android.ndk_api = 21

# (bool) Use --private data storage (True) or --dir public storage (False)
#android.private_storage = True

# (str) Android entry point, default is ok for Kivy-based app
#android.entrypoint = org.kivy.android.PythonActivity

# (list) Pattern to whitelist for the whole project
#android.whitelist =

# (bool) If True, then skip trying to update the Android sdk
# This can be useful to avoid excess Internet downloads or save time
# when an update is due and you just want to test/build your package
# android.skip_update = False

# (bool) If True, then automatically accept SDK license
# agreements. This is intended for automation only. If set to False,
# the default, you will be shown the license when first running
# buildozer.
# android.accept_sdk_license = False

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.arch = armeabi-v7a

# (int) overrides automatic versionCode computation (used in build.gradle)
# this is not the same as app version and should only be edited if you know what you're doing
# android.numeric_version = 1

# (bool) enables Android auto backup feature (Android API >=23)
# android.allow_backup = True

# (str) XML file for custom backup rules (see official auto backup documentation)
# android.backup_rules =

# (str) If you need to insert variables into your AndroidManifest.xml file,
# you can do it here. This is equivalent to using the --manifest-placeholders
# flag when building.
# android.manifest_placeholders = { "key": "value", "key2": "value2" }

# (bool) Copy library instead of making a libpymodules.so
# android.copy_libs = 1

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.archs = arm64-v8a, armeabi-v7a

# (bool) enables Android auto backup feature (Android API >=23)
# android.allow_backup = True

# (str) XML file for custom backup rules (see official auto backup documentation)
# android.backup_rules =

# (bool) If True, then the application is not listed in the application launcher.
# android.no_history = False

# (bool) If True, then the application is not listed in the task list.
# android.exclude_from_recents = False

# (list) Manifest metadata to add (key=value format)
# android.meta_data =

# (list) Android features to add (feature=required format)
# android.features =

# (list) Library features to add (library=required format)
# android.library_features =

# (list) Activity intent filters to add (action=category format)
# android.intent_filters =

# (list) Service intent filters to add (action=category format)
# android.service_intent_filters =

# (list) Android Manifest uses-feature to add
# android.uses_features =

# (list) Android Manifest uses-library to add
# android.uses_library =

# (list) Android activities to add
# android.add_activities =

# (list) Android service to add
# android.add_services =

# (list) Android receivers to add
# android.add_receivers =

# (list) Android meta-data to add
# android.add_meta_data =

# (list) Android package to add
# android.add_package =

# (list) Android scritps to add
# android.add_scripts =

# (list) Android permissions to add
# android.add_permissions =

# (list) Android raw resources to add
# android.add_raw =

# (list) Android assets to add
# android.add_assets =

# (list) Android Java classes to add
# android.add_java_src =

# (list) Android Java libraries to add
# android.add_java_libs =

# (list) Android AAR libraries to add
# android.add_aars =

# (list) Android gradle dependencies to add
# android.gradle_dependencies =

# (bool) Enable AndroidX support. Enable when 'android.gradle_dependencies'
# contains an 'androidx' package, or any package that depends on AndroidX.
# android.enable_androidx = False

# (list) Java classes to add as activities to the manifest.
# android.add_activities =

# (list) Python files to add to the asset folder
# android.add_python_src =

# (list) Java files to add to the asset folder
# android.add_java_src =

# (list) Python modules to add to the asset folder
# android.add_python_libs =

# (list) Python packages to add to the asset folder
# android.add_python_packages =

# (list) Python eggs to add to the asset folder
# android.add_python_eggs =

# (list) Python wheels to add to the asset folder
# android.add_python_wheels =

# (list) Python modules to exclude from the asset folder
# android.exclude_python_libs =

# (list) Python packages to exclude from the asset folder
# android.exclude_python_packages =

# (list) Python eggs to exclude from the asset folder
# android.exclude_python_eggs =

# (list) Python wheels to exclude from the asset folder
# android.exclude_python_wheels =

# (list) Python modules to add to the site-packages
# android.add_site_packages =

# (list) Python packages to add to the site-packages
# android.add_site_packages =

# (list) Python eggs to add to the site-packages
# android.add_site_packages =

# (list) Python wheels to add to the site-packages
# android.add_site_packages =

# (list) Python modules to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python packages to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python eggs to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python wheels to exclude from the site-packages
# android.exclude_site_packages =

# (bool) Copy library instead of making a libpymodules.so
# android.copy_libs = 1

# (str) The Android arch to build for, choices: armeabi-v7a, arm64-v8a, x86, x86_64
# android.archs = arm64-v8a, armeabi-v7a

# (bool) enables Android auto backup feature (Android API >=23)
# android.allow_backup = True

# (str) XML file for custom backup rules (see official auto backup documentation)
# android.backup_rules =

# (bool) If True, then the application is not listed in the application launcher.
# android.no_history = False

# (bool) If True, then the application is not listed in the task list.
# android.exclude_from_recents = False

# (list) Manifest metadata to add (key=value format)
# android.meta_data =

# (list) Android features to add (feature=required format)
# android.features =

# (list) Library features to add (library=required format)
# android.library_features =

# (list) Activity intent filters to add (action=category format)
# android.intent_filters =

# (list) Service intent filters to add (action=category format)
# android.service_intent_filters =

# (list) Android Manifest uses-feature to add
# android.uses_features =

# (list) Android Manifest uses-library to add
# android.uses_library =

# (list) Android activities to add
# android.add_activities =

# (list) Android service to add
# android.add_services =

# (list) Android receivers to add
# android.add_receivers =

# (list) Android meta-data to add
# android.add_meta_data =

# (list) Android package to add
# android.add_package =

# (list) Android scritps to add
# android.add_scripts =

# (list) Android permissions to add
# android.add_permissions =

# (list) Android raw resources to add
# android.add_raw =

# (list) Android assets to add
# android.add_assets =

# (list) Android Java classes to add
# android.add_java_src =

# (list) Android Java libraries to add
# android.add_java_libs =

# (list) Android AAR libraries to add
# android.add_aars =

# (list) Android gradle dependencies to add
# android.gradle_dependencies =

# (bool) Enable AndroidX support. Enable when 'android.gradle_dependencies'
# contains an 'androidx' package, or any package that depends on AndroidX.
# android.enable_androidx = False

# (list) Java classes to add as activities to the manifest.
# android.add_activities =

# (list) Python files to add to the asset folder
# android.add_python_src =

# (list) Java files to add to the asset folder
# android.add_java_src =

# (list) Python modules to add to the asset folder
# android.add_python_libs =

# (list) Python packages to add to the asset folder
# android.add_python_packages =

# (list) Python eggs to add to the asset folder
# android.add_python_eggs =

# (list) Python wheels to add to the asset folder
# android.add_python_wheels =

# (list) Python modules to exclude from the asset folder
# android.exclude_python_libs =

# (list) Python packages to exclude from the asset folder
# android.exclude_python_packages =

# (list) Python eggs to exclude from the asset folder
# android.exclude_python_eggs =

# (list) Python wheels to exclude from the asset folder
# android.exclude_python_wheels =

# (list) Python modules to add to the site-packages
# android.add_site_packages =

# (list) Python packages to add to the site-packages
# android.add_site_packages =

# (list) Python eggs to add to the site-packages
# android.add_site_packages =

# (list) Python wheels to add to the site-packages
# android.add_site_packages =

# (list) Python modules to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python packages to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python eggs to exclude from the site-packages
# android.exclude_site_packages =

# (list) Python wheels to exclude from the site-packages
# android.exclude_site_packages =

#
# iOS specific
#

# (str) Path to a custom kivy-ios folder
# ios.kivy_ios_dir = ../kivy-ios
# Alternately, specify the URL and branch of a git checkout:
ios.kivy_ios_url = https://github.com/kivy/kivy-ios
ios.kivy_ios_branch = master

# (str) Name of the certificate to use for signing the debug version
# Get a list of available identities: security find-identity -v -p codesigning
# ios.codesign.debug = "iPhone Developer: <lastname> <firstname> (<hexstring>)"

# (str) Name of the certificate to use for signing the release version
# ios.codesign.release = %(ios.codesign.debug)s

# (str) The type of signing identity to use
# ios.codesign.type = debug

# (list) The development team to use for signing the app
# ios.codesign.development_team = <hexstring>

# (bool) Use the entitlements file to sign the app
# ios.codesign.entitlements = True

# (str) Path to the entitlements file to use
# ios.codesign.entitlements_file = %(source.dir)s/entitlements.plist

# (str) Path to the mobileprovision file to use
# ios.codesign.mobileprovision = %(source.dir)s/profile.mobileprovision

# (list) List of frameworks to link against
# ios.frameworks = AudioToolbox

# (list) List of extra libraries to link against
# ios.libraries =

# (str) Path to the Info.plist file to use
# ios.info_plist = %(source.dir)s/Info.plist

# (list) List of frameworks to link against
# ios.frameworks = AudioToolbox

# (list) List of extra libraries to link against
# ios.libraries =

# (str) Path to the Info.plist file to use
# ios.info_plist = %(source.dir)s/Info.plist

# (str) The bundle identifier of the application
# ios.bundle_identifier = org.test

# (str) The bundle name of the application
# ios.bundle_name = giftapp

# (str) The bundle version of the application
# ios.bundle_version = 1.0

# (str) The display name of the application
# ios.display_name = Gift App

# (str) The version of the application
# ios.version = 0.1

# (str) The author of the application
# ios.author = Jules

# (str) The email of the author of the application
# ios.email =

# (str) The website of the author of the application
# ios.website =

# (str) The copyright of the application
# ios.copyright =

# (bool) Enable/disable bitcode
# ios.bitcode = False

# (bool) Enable/disable armv7
# ios.arch_armv7 = True

# (bool) Enable/disable arm64
# ios.arch_arm64 = True

# (bool) Enable/disable x86
# ios.arch_x86 = True

# (bool) Enable/disable x86_64
# ios.arch_x86_64 = True

# (bool) Enable/disable simulator
# ios.simulator = True

# (bool) Enable/disable debug
# ios.debug = True

# (bool) Enable/disable verbose
# ios.verbose = True

# (bool) Enable/disable clean
# ios.clean = True

# (bool) Enable/disable codesign
# ios.codesign = True

# (bool) Enable/disable strip
# ios.strip = True

# (bool) Enable/disable ipa
# ios.ipa = True

# (bool) Enable/disable deploy
# ios.deploy = True

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = False, 1 = True)
warn_on_root = 1

# (str) Path to build artifact storage, absolute or relative to spec file
# build_dir = ./.buildozer

# (str) Path to build output storage, absolute or relative to spec file
# bin_dir = ./bin

# (str) Path to the android SDK to use
# android.sdk_path =

# (str) Path to the android NDK to use
# android.ndk_path =

# (str) Path to the android ANT to use
# android.ant_path =

# (str) Path to the android HOME to use
# android.home =

# (str) Path to the python to use
# python_path =

# (str) Path to the p4a directory to use
# p4a_dir =

# (str) Path to the buildozer directory to use
# buildozer_dir =

# (str) Path to the buildozer state file to use
# buildozer_state_file =
