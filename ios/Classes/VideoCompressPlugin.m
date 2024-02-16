#import "VideoCompressPlugin.h"
#import <video_compress_lite/video_compress_lite-Swift.h>

@implementation VideoCompressPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftVideoCompressPlugin registerWithRegistrar:registrar];
}
@end
