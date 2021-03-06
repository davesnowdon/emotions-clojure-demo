emotions-clojure-demo
=====================

Demo application for emotions framework running in clojure with interface to NAO robot.

The application can be run with text output on the command line or as a web application.


Running in command-line mode
----------------------------

When run like this the application simply prints the valence, arousal and satisfaction to standard output after each upadate.
Any percepts processed during the update are also printed.


<pre>lein trampoline run [ROBOT HOSTNAME/IP]</pre>


Running as a web application
----------------------------

When run as an application the server listens on port 3000 and uses a websocket to send updates to connected web browsers.
In addition to the current numeric values for valence, arousal and the satisfaction vector there is also a graphical display of theses values
as bar charts.

In addition the complete state of the motivations and their parameters are shown.

Start the application using leiningen.

<pre>lein dev</pre>

Once leiningen has printed that the web server is running you can connect your browser to http://localhost:3000 Currently chrome seems to work best.

At the bottom of the page is a form that allows you to enter the IP address or hostname of a NAO and connect the application to this robot.
This will allow the emotional model to receive sensory input from the robot and to use the robot to display reactions such as happiness and annoyance.

The web application uses [Facebook's React framework](http://reactjs.org/) and David Nolen's [Om](https://github.com/swannodette/om)

Dependencies
============

Most dependencies are available on clojars or maven central and no special action is required since leiningen will fetch them automatically.

There two dependencies however, that need to be manually installed

## emotions-clojure

https://github.com/davesnowdon/emotions-clojure

Use <pre>lein install</pre> to install this in your local repository.


## Packaging the Aldebaran Java SDK
You need to install the Aldebaran jar file for your platform on locally.  You can use the
[lein-localrepo](https://github.com/kumarshantanu/lein-localrepo) plug
in. Run:

    lein localrepo install java-naoqi-sdk-2.1.4.13-mac64.jar com.aldebaran/java-naoqi 2.1.4


TODO
====

* Make the motivation parameters editable in the web application so that it can be used to help tune and adjust the parameters for the model.
* Display historical values of the satisfaction vector, valence & arousal using line charts.

## License

Copyright © 2014 Dave Snowdon and contributors

Distributed under the Eclipse Public License, the same as Clojure.
