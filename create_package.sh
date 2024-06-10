#!/bin/sh
zip -vr app.zip . -x "app.zip" -x "*.DS_Store" -x ".git/*" -x "*.git" -x ".env" -x ".github/*" -x ".idea/*" -x "mvnw.cmd" -x "gradlew.bat" -x "create_package.sh" -x ".gitignore"