============
Coding Style
============

Like all development teams, the `Dataverse developers at IQSS <https://dataverse.org/about>`_ have their habits and styles when it comes to writing code. Let's attempt to get on the same page. :)

.. contents:: |toctitle|
	:local:

Java
----

Formatting Code
~~~~~~~~~~~~~~~

Rules can be seen here: https://wiki.yadda.icm.edu.pl/yadda/Public:Coding_conventions

.. _format_code_netbeans:

Format Code You Changed with Netbeans
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

IQSS has standardized on Netbeans. It is much appreciated when you format your code (but only the code you touched!) using the out-of-the-box Netbeans configuration. If you have created an entirely new Java class, you can just click Source -> Format. If you are adjusting code in an existing class, highlight the code you changed and then click Source -> Format. Keeping the "diff" in your pull requests small makes them easier to code review.

Checking Your Formatting With Checkstyle
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The easiest way to adopt Dataverse coding style is to use Netbeans as your IDE, avoid change the default Netbeans formatting settings, and only reformat code you've changed, as described in :ref:`format_code_netbeans`.

If you do not use Netbeans, you are encouraged to check the formatting of your code using Checkstyle.

To check the entire project:

``mvn checkstyle:checkstyle``

To check a single file:

``mvn checkstyle:checkstyle -Dcheckstyle.includes=**\/SystemConfig*.java``

Logging
~~~~~~~

We have adopted a pattern where the top of every class file has a line like this::

    private static final Logger logger = Logger.getLogger(DatasetUtil.class.getCanonicalName());

Use this ``logger`` field with varying levels such as ``fine`` or ``info`` like this::

    logger.fine("will get thumbnail from dataset logo");

Generally speaking you should use ``fine`` for everything that you don't want to show up in Glassfish's ``server.log`` file by default. If you use a higher level such as ``info`` for common operations, you will probably hear complaints that your code is too "chatty" in the logs. These logging levels can be controlled at runtime both on your development machine and in production as explained in the :doc:`debugging` section.

When adding logging, do not simply add ``System.out.println()`` lines because the logging level cannot be controlled.

Bash
----

Generally, Google's Shell Style Guide at https://google.github.io/styleguide/shell.xml seems to have good advice.

Formatting Code
~~~~~~~~~~~~~~~

Tabs vs. Spaces
^^^^^^^^^^^^^^^

Don't use tabs. Use 2 spaces.

shfmt from https://github.com/mvdan/sh seems like a decent way to enforce indentation of two spaces (i.e. ``shfmt -i 2 -w path/to/script.sh``) but be aware that it makes other changes.

----

Previous: :doc:`debugging` | Next: :doc:`deployment`
