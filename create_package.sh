#!/bin/sh
zip -vr app.zip . -x "*.DS_Store" -x ".git/*" -x "*.git" -x ".env" -x ".github/*" -x "target/*" -x "build/*" -x "create_package.sh" -x ".gitignore"