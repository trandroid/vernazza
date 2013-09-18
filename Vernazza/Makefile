
APK_DEBUG := bin/Vernazza.apk
APK_UNSIGNED := bin/Vernazza-release-unsigned.apk
APK_UNALIGNED := bin/Vernazza_unaligned.apk
APK_SIGNED := dist/latest/Vernazza.apk

build.xml:
	android update project --path .

$(APK_UNSIGNED): build.xml $(APK_DEBUG)
	ant release

$(APK_UNALIGNED): $(APK_UNSIGNED)
	mkdir -p dist
	cp -f $(APK_UNSIGNED) $@
	jarsigner -verbose -sigalg MD5withRSA -digestalg SHA1 -keystore ../../keystore/manhdev.keystore $@ manhdev
	
$(APK_SIGNED): $(APK_UNALIGNED)
	mkdir -p dist/latest
	rm -f $@
	zipalign -v 4 $(APK_UNALIGNED) $@

dist: $(APK_SIGNED)

install: dist
	adb install -r $(APK_SIGNED)

uninstall:
	adb uninstall com.manhdev.vernazza

clean:
	rm -rf bin/*.apk
