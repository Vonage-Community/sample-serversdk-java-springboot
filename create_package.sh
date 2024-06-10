#!/bin/sh
zip -vr app.zip . -x "*.DS_Store" -x ".git/*" -x "*.git" -x ".env" -x ".github/*" -x "create_package.sh" -x ".gitignore"