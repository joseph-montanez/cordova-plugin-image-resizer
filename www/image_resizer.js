var ImageResizer = function() {};

ImageResizer.prototype.resize = function(options, success, fail) {
  cordova.exec(function(uri) {
    success(uri);
  }, function() {
    fail();
  }, "ImageResizer", "resize", [options]);
};

ImageResizer.prototype.correctOrientation = function(options, success, fail) {
  cordova.exec(function(uri) {
    success(uri);
  }, function() {
    fail();
  }, "ImageResizer", "correctOrientation", [options]);
};

var imageResizer = new ImageResizer();
module.exports = imageResizer;
