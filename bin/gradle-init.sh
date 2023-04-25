#!/bin/sh

# Creates a template for a composite build with build logic and Kotlin DSL, which is the preferred configuration for the XVM repository

binDir=$(dirname "$BASH_SOURCE")
$binDir/../gradlew init --project-name xvm --type java-application --dsl kotlin --package org.xvm --test-framework junit --split-project --incubating
