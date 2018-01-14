#!/bin/bash
echo " starting EchoSocket mock server for Lang2"
exec ruby ./EchoSocket2.rb >> rubyLog.log
