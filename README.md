SphinxUtil
==========

A Python [Sphinx](http://sphinx.pocoo.org/) Toolkit written in Java.

Currently the following utilities are available.

Intersphinx Objects.inv encoder-decoder
---------------------------------------

* The class `EncodeDecodeObjectInv` is capable of encoding and decoding [objects.inv](http://sphinx.pocoo.org/latest/ext/intersphinx.html) files. This class has a `main()` method, so run it to know how to use it.

* Class `Django` is specialized to 'fix' Django's `objects.inv` files. The original ones which can be downloaded from `http://docs.djangoproject.com/en/<version>/_objects/` do not have fully qualified references for some classes. Take for example, `django.forms.Select`. Although this reference is correct, but when using [autodoc](http://sphinx.pocoo.org/ext/autodoc.html) and `show-inheritance` option, it will generate the path `django.forms.widgets.Select`, which is the actual path. Unfortunately Django's provided `objects.inv` has no reference for `django.forms.widgets.Select`, resulting in `intersphinx` unable to link such references.

    This class will go over the known patterns in the original `objects.inv` and create a new one with all the missing references. Note that, `main()` of class `EncodeDecodeObjectInv` exposes this class for the user.
