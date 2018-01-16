#!/bin/bash
# to be run on bash windows before maven run with integration tests
export LSPLANG2_protocol=socket-client
export LSPLANG1_outport=8990
export LSPLANG2_exec=util\EchoLauncher2.bat
export LSPLANG1_STDIN_PORT=8991
export lspservers=lang1,lang2
export LSPLANG1_workdir=util\
export LSPLANG2_clientport=8765
export LSPLANG1_inport=8991
export LSPLANG1_protocol=socket
export basedir=\src\test\
export LSPLANG1_exec=util\EchoLauncher1.bat
export LSPLANG1_STDOUT_PORT=8990
export LSPLANG2_workdir=util\
export DiToken=THEDITOKEN