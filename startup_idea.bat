del /q bootstrap.jar
jar cvf0 bootstrap.jar -C out/production/DIYtomcat priv/mika/diytomcat/Bootstrap.class -C out/production/DIYtomcat priv/mika/diytomcat/classloader/CommonClassLoader.class
del /q lib/DIYtomcat.jar
cd out
cd production
cd diytomcat
jar cvf0 ../../../lib/diytomcat.jar *
cd ..
cd ..
cd ..
java -cp bootstrap.jar priv.mika.diytomcat.Bootstrap
pause