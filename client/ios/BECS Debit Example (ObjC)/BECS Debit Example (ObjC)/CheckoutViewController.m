//
//  CheckoutViewController.m
//  BECS Debit Example (ObjC)
//
//  Created by Cameron Sabol on 3/26/20.
//  Copyright © 2020 Stripe. All rights reserved.
//

#import "CheckoutViewController.h"

#import <Stripe/Stripe.h>

/**
* To run this app, you'll need to first run the sample server locally.
* Follow the "How to run locally" instructions in the root directory's README.md to get started.
* Once you've started the server, open http://localhost:4242 in your browser to check that the
* server is running locally.
* After verifying the sample server is running locally, build and run the app using the iOS simulator.
*/
NSString *const BackendUrl = @"http://127.0.0.1:4242/";

@interface CheckoutViewController () <STPAUBECSDebitFormViewDelegate, STPAuthenticationContext>

@property (nonatomic) STPAUBECSDebitFormView *becsFormView;
@property (nonatomic, readonly) UIButton *payButton;

@property (nonatomic, copy) NSString *paymentIntentClientSecret;

@end

@implementation CheckoutViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];

    _payButton = [UIButton buttonWithType:UIButtonTypeCustom];
    self.payButton.layer.cornerRadius = 5;
    self.payButton.backgroundColor = [UIColor systemGray3Color];
    self.payButton.titleLabel.font = [UIFont systemFontOfSize:18];
    self.payButton.contentEdgeInsets = UIEdgeInsetsMake(4, 8, 4, 8);
    self.payButton.enabled = NO;
    [self.payButton setTitle:@"Accept Mandate and Pay" forState:UIControlStateNormal];
    [self.payButton addTarget:self action:@selector(pay) forControlEvents:UIControlEventTouchUpInside];
    self.payButton.translatesAutoresizingMaskIntoConstraints = NO;

    [self.view addSubview:self.payButton];

    [NSLayoutConstraint activateConstraints:@[
        [self.payButton.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
    ]];

    [self _addBECSFormView];

    [self getPublishableKey];
}

- (void)_addBECSFormView {
    self.becsFormView = [[STPAUBECSDebitFormView alloc] initWithCompanyName:@"Example Company Inc."];
    self.becsFormView.becsDebitFormDelegate = self;
    self.becsFormView.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:self.becsFormView];

    [NSLayoutConstraint activateConstraints:@[
        // the leading and trailing anchors explicitly do not constrain to the
        // safeAreaLayoutGuide because STPAUBECSDebitFormView automatically
        // lays out its contents respecting all margins and we want the background
        // to extend to the screen edges
        [self.becsFormView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.view.trailingAnchor constraintEqualToAnchor:self.becsFormView.trailingAnchor],

        [self.becsFormView.topAnchor constraintEqualToSystemSpacingBelowAnchor:self.view.safeAreaLayoutGuide.topAnchor multiplier:2],

        [self.payButton.topAnchor constraintEqualToSystemSpacingBelowAnchor:self.becsFormView.bottomAnchor multiplier:2],
    ]];
}

- (void)displayAlertWithTitle:(NSString *)title message:(NSString *)message restartDemo:(BOOL)restartDemo {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:title message:message preferredStyle:UIAlertControllerStyleAlert];
        if (restartDemo) {
            [alert addAction:[UIAlertAction actionWithTitle:@"Restart demo" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
                [self.becsFormView removeFromSuperview];
                [self _addBECSFormView];
                [self startCheckout];
            }]];
        } else {
            [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
        }
        [self presentViewController:alert animated:YES completion:nil];
    });
}

- (void)getPublishableKey {
    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@config", BackendUrl]];
    NSMutableURLRequest *request = [[NSURLRequest requestWithURL:url] mutableCopy];
    [request setHTTPMethod:@"GET"];
    NSURLSessionTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *requestError) {
        NSError *error = requestError;
        if (data != nil) {
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
            if (error != nil || httpResponse.statusCode != 200 || json[@"publishableKey"] == nil) {
                [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];
            } else {
                NSString *stripePublishableKey = json[@"publishableKey"];
                // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
                [Stripe setDefaultPublishableKey:stripePublishableKey];
                [self startCheckout];
            }
        } else {
            [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];

        }
    }];
    [task resume];
}

- (void)startCheckout {
    // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@create-payment-intent", BackendUrl]];
    NSMutableURLRequest *request = [[NSURLRequest requestWithURL:url] mutableCopy];
    [request setHTTPMethod:@"POST"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [request setHTTPBody:[NSJSONSerialization dataWithJSONObject:@{@"items": @1, @"currency": @"eur"} options:0 error:NULL]];
    NSURLSessionTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *requestError) {
        NSError *error = requestError;
        if (data != nil) {
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
            if (error != nil || httpResponse.statusCode != 200 || json[@"clientSecret"] == nil) {
                [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];
            } else {
                self.paymentIntentClientSecret = json[@"clientSecret"];
            }
        } else {
            [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];

        }
    }];
    [task resume];
}

- (void)pay {

    if (self.paymentIntentClientSecret == nil || self.becsFormView.paymentMethodParams == nil) {
        return;
    }

    STPPaymentIntentParams *paymentIntentParams = [[STPPaymentIntentParams alloc] initWithClientSecret:self.paymentIntentClientSecret];

    paymentIntentParams.paymentMethodParams = self.becsFormView.paymentMethodParams;

    [[STPPaymentHandler sharedHandler] confirmPayment:paymentIntentParams
                            withAuthenticationContext:self
                                           completion:^(STPPaymentHandlerActionStatus handlerStatus, STPPaymentIntent * handledIntent, NSError * _Nullable handlerError) {
        switch (handlerStatus) {
            case STPPaymentHandlerActionStatusFailed:
                [self displayAlertWithTitle:@"Payment failed" message:handlerError.localizedDescription ?: @"" restartDemo:NO];
                break;
            case STPPaymentHandlerActionStatusCanceled:
                [self displayAlertWithTitle:@"Canceled" message:handlerError.localizedDescription ?: @"" restartDemo:NO];
                break;
            case STPPaymentHandlerActionStatusSucceeded:
                [self displayAlertWithTitle:@"Payment successfully created" message:handlerError.localizedDescription ?: @"" restartDemo:YES];
                break;
        }
    }];
}

#pragma mark - STPAuthenticationContext
- (UIViewController *)authenticationPresentingViewController {
    return self;
}

#pragma mark - STPAUBECSDebitFormViewDelegate
- (void)auBECSDebitForm:(STPAUBECSDebitFormView *)form didChangeToStateComplete:(BOOL)complete {
    self.payButton.enabled = complete;
    self.payButton.backgroundColor = complete ? [UIColor systemBlueColor] : [UIColor systemGray3Color];
}


@end
