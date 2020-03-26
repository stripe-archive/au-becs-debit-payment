//
//  AppDelegate.m
//  BECS Debit Example (ObjC)
//
//  Created by Cameron Sabol on 3/23/20.
//  Copyright © 2020 Stripe. All rights reserved.
//

#import "AppDelegate.h"

#import <Stripe/Stripe.h>

#import "CheckoutViewController.h"

@interface AppDelegate ()

@property (nonatomic) UIWindow *customWindow;

@end

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    UIViewController *rootVC = [[CheckoutViewController alloc] init];
    UIWindow *window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
    window.rootViewController = rootVC;
    [window makeKeyAndVisible];
    self.customWindow = window;
    return YES;
}

- (BOOL)application:(UIApplication *)application openURL:(nonnull NSURL *)url options:(nonnull NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options {
    BOOL stripeHandled = [Stripe handleStripeURLCallbackWithURL:url];
    if (stripeHandled) {
        return YES;
    }
    return NO;
}

@end
