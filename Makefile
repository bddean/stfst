# Build the keyboard and put it on the phone: `make install`.
#
# The dictionaries are not rebuilt here. `make dicts` runs the HFST pipeline,
# which takes minutes, and everything else is seconds.

ADB ?= $(or $(shell command -v adb),$(HOME)/Library/Android/sdk/platform-tools/adb)
GRADLE ?= $(or $(shell command -v gradle),nix run nixpkgs\#gradle --)
CARGO ?= cargo

APK := android/app/build/outputs/apk/release/app-release.apk
LIB := android/app/src/main/jniLibs/arm64-v8a/libstfst.so
ASSETS := android/app/src/main/assets/runtime
DICTS := build/dicts/.compiled

RUST_SRC := $(shell find crates -name '*.rs') Cargo.toml Cargo.lock
APP_SRC := $(shell find android/app/src -type f -not -path '*/assets/*')

.PHONY: install apk native assets dicts regen test sync-user clean-apk

install: $(APK)
	$(ADB) install -r $(APK)
	@$(MAKE) sync-user
	@echo "installed; enable it under Settings > Keyboards if this is a first install"

# What the keyboard defined, into the desktop's copy and back again. Separate
# from the install because it is the only step that reads the phone.
sync-user:
	android/sync-user.sh

apk: $(APK)
native: $(LIB)
assets: $(ASSETS)

$(APK): $(LIB) $(ASSETS) $(APP_SRC) android/app/build.gradle.kts android/build.gradle.kts
	cd android && $(GRADLE) :app:assembleRelease

$(LIB): $(RUST_SRC)
	android/build-native.sh

# The compiled dictionaries, the key system, the dictionaries the app reads as
# files rather than as artifacts, and the machines' configuration.
$(ASSETS): $(DICTS) systems/stenotype-extended.toml android/editable.toml dictionaries/user.json $(wildcard $(HOME)/.config/plover/plover.cfg)
	android/sync-assets.sh

# A directory's mtime says nothing about what is under it, so the compile
# stamps itself.
$(DICTS): recipe.toml $(wildcard dictionaries/*)
	$(CARGO) run --release -p stfst-cli -- compile recipe.toml
	touch $@

dicts: $(DICTS)

# The generators, run until each is current: see scripts/regen.sh.
regen:
	scripts/regen.sh

test:
	cd android && $(GRADLE) :app:testDebugUnitTest

clean-apk:
	rm -rf android/app/build/outputs
