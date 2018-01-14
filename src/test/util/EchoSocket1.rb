require 'socket'

clientIn = TCPSocket.new 'localhost', ENV["STDIN_PORT"].to_i #8991
clientOut = TCPSocket.new 'localhost', ENV["STDOUT_PORT"].to_i #8990

puts "Connection is opened"

begin
  while  msg = clientIn.recv( 1024 )  do # Read message from socket
    puts msg
    clientOut.send msg, 0
  end
rescue SystemExit, Interrupt
  raise
rescue Exception => e
  puts "EOF........"
  clientIn.close_read             # close socket when done
  clientOut.close_write
end  

