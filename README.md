# akka-http-websockets  

WebSockets  
Scala  
Akka  
Akka Http  
Akka Stream  
Circe
Scala Test


Used "Dark WebSocket Terminal" Chrome browser extension for testing.

IntelliJ IDEA 2018.1.4 (Community Edition)


`sbt run`  


Feedback from 2019-8-29:
1. Replace LazyLogging, ActorLogging with LoggingAdapter.
2. Don't use Ask (?) pattern.
Map callback Future with pipeTo(self)
