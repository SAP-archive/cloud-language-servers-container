#!/bin/bash
echo "staring EchoSocket mock server"
exec ruby ./EchoSocket.rb >> rubyLog.log
