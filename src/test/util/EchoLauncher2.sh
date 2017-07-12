#!/bin/bash
echo "staring EchoSocket mock server for CDX"
exec ruby ./EchoSocket2.rb >> rubyLog.log
