require 'socket'

server = TCPServer.open(8765)
client = server.accept

puts "Connection is opened"

begin
  while  msg = client.recv( 1024 )  do # Read message from socket
    puts msg
    client.send msg, 0
  end
rescue SystemExit, Interrupt
  raise
rescue Exception => e
  puts "EOF........"
  client.close            # close socket when done
end  

