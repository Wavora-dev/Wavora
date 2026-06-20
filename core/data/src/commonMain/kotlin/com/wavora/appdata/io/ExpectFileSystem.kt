package com.wavora.appdata.io

import okio.FileSystem

expect fun fileSystem(): FileSystem

expect fun fileDir(): String