#!/bin/bash
gradle --no-daemon assembleDebug 2>&1 | tee /tmp/gradle_output.txt
exit ${PIPESTATUS[0]}
