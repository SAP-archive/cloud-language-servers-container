#!/bin/bash
echo " starting EchoSocket mock server for CDX"
exec ruby ./EchoSocket2.rb >> rubyLog.log
