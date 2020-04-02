package com.example.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.app.databinding.CheckoutActivityBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.stripe.android.ApiResultCallback;
import com.stripe.android.PaymentIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.model.PaymentMethodCreateParams;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CheckoutActivityJava extends AppCompatActivity {

    private CheckoutActivityBinding viewBinding;
    private CheckoutViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewBinding = CheckoutActivityBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(
                this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication())
        ).get(CheckoutViewModel.class);

        setContentView(viewBinding.getRoot());

        viewBinding.becsDebitWidget.setValidParamsCallback(isValid -> {
            viewBinding.payButton.setEnabled(isValid);
        });

        viewBinding.payButton.setOnClickListener(v -> {
            final PaymentMethodCreateParams params = viewBinding.becsDebitWidget.getParams();
            if (params != null) {
                onPayClicked(params);
            }
        });

        viewBinding.progressBar.setVisibility(View.VISIBLE);
        viewModel.getPublishableKey().observe(
                this,
                fetchResult -> {
                    viewBinding.progressBar.setVisibility(View.INVISIBLE);
                    if (fetchResult instanceof CheckoutViewModel.FetchResult.Success) {
                        final CheckoutViewModel.FetchResult.Success successResult =
                                (CheckoutViewModel.FetchResult.Success) fetchResult;
                        final String publishableKey = successResult
                                .data
                                .optString("publishableKey");
                        onPublishableKeyFetched(publishableKey);
                    } else if (fetchResult instanceof CheckoutViewModel.FetchResult.Error) {
                        final CheckoutViewModel.FetchResult.Error errorResult =
                                (CheckoutViewModel.FetchResult.Error) fetchResult;
                        displayError(errorResult.exception);
                    }
                }
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        viewBinding.progressBar.setVisibility(View.VISIBLE);
        viewModel.onPaymentResult(requestCode, data).observe(
                this,
                paymentResult -> {
                    viewBinding.progressBar.setVisibility(View.INVISIBLE);
                    if (paymentResult instanceof CheckoutViewModel.PaymentResult.Success) {
                        final CheckoutViewModel.PaymentResult.Success successResult =
                                (CheckoutViewModel.PaymentResult.Success) paymentResult;
                        displayPaymentResult(successResult.result);
                    } else if (paymentResult instanceof CheckoutViewModel.PaymentResult.Error) {
                        final CheckoutViewModel.PaymentResult.Error errorResult =
                                (CheckoutViewModel.PaymentResult.Error) paymentResult;
                        displayError(errorResult.exception);
                    }
                }
        );
    }

    private void onPayClicked(@NonNull final PaymentMethodCreateParams params) {
        final Stripe stripe = viewModel.getStripe();
        if (stripe != null) {
            stripe.confirmPayment(
                    this,
                    ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                            params,
                            viewModel.clientSecret
                    )
            );
        }
    }

    private void displayPaymentResult(@NonNull PaymentIntentResult paymentIntentResult) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Success")
                .setMessage("Outcome: " + paymentIntentResult.getOutcome())
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    private void displayError(@NonNull Exception ex) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(ex.getMessage())
                .setNeutralButton(android.R.string.ok, null)
                .create()
                .show();
    }

    private void onPublishableKeyFetched(@NonNull String publishableKey) {
        viewModel.publishableKey = publishableKey;

        viewBinding.progressBar.setVisibility(View.VISIBLE);

        viewModel.createPaymentIntent(new JSONObject()).observe(
                this,
                fetchResult -> {
                    viewBinding.progressBar.setVisibility(View.INVISIBLE);
                    if (fetchResult instanceof CheckoutViewModel.FetchResult.Success) {
                        final CheckoutViewModel.FetchResult.Success successResult =
                                (CheckoutViewModel.FetchResult.Success) fetchResult;
                        onClientSecretFetched(
                                successResult.data.optString("clientSecret")
                        );
                    } else if (fetchResult instanceof CheckoutViewModel.FetchResult.Error) {
                        final CheckoutViewModel.FetchResult.Error errorResult =
                                (CheckoutViewModel.FetchResult.Error) fetchResult;
                        displayError(errorResult.exception);
                    }
                }
        );
    }

    private void onClientSecretFetched(@NonNull String clientSecret) {
        viewModel.clientSecret = clientSecret;
    }

    public static class CheckoutViewModel extends AndroidViewModel {
        private final Context context = getApplication().getApplicationContext();
        private final OkHttpClient httpClient = new OkHttpClient();

        private String publishableKey = null;
        private String clientSecret = null;

        public CheckoutViewModel(@NonNull Application application) {
            super(application);
        }

        @Nullable
        Stripe getStripe() {
            if (publishableKey != null) {
                return new Stripe(context, publishableKey);
            } else {
                return null;
            }
        }

        @NonNull
        LiveData<FetchResult> getPublishableKey() {
            final MutableLiveData<FetchResult> liveData = new MutableLiveData<>();

            final Request request = new Request.Builder()
                    .url(BACKEND_URL + "/config")
                    .get()
                    .build();
            httpClient.newCall(request)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            liveData.postValue(
                                    new FetchResult.Error(e)
                            );
                        }

                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response)
                                throws IOException {
                            if (!response.isSuccessful()) {
                                liveData.postValue(
                                        new FetchResult.Error(
                                                new IOException("Response unsuccessful")
                                        )
                                );
                            } else {
                                final ResponseBody responseBody = response.body();
                                final String responseData = responseBody.string();
                                try {
                                    liveData.postValue(
                                            new FetchResult.Success(new JSONObject(responseData))
                                    );
                                } catch (JSONException e) {
                                    liveData.postValue(
                                            new FetchResult.Error(e)
                                    );
                                }
                            }
                        }
                    });

            return liveData;
        }

        @NonNull
        LiveData<FetchResult> createPaymentIntent(@NonNull JSONObject body) {
            final MutableLiveData<FetchResult> liveData = new MutableLiveData<>();
            final Request request = new Request.Builder()
                    .url(BACKEND_URL + "/create-payment-intent")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MEDIA_TYPE))
                    .build();

            httpClient.newCall(request)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            liveData.postValue(
                                    new FetchResult.Error(e)
                            );
                        }

                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response)
                                throws IOException {
                            if (!response.isSuccessful()) {
                                liveData.postValue(
                                        new FetchResult.Error(
                                                new IOException("Response unsuccessful")
                                        )
                                );
                            } else {
                                final String responseData = response.body().string();
                                try {
                                    liveData.postValue(
                                            new FetchResult.Success(new JSONObject(responseData))
                                    );
                                } catch (JSONException e) {
                                    liveData.postValue(new FetchResult.Error(e));
                                }
                            }
                        }
                    });
            return liveData;
        }

        @NonNull
        MutableLiveData<PaymentResult> onPaymentResult(
                int requestCode,
                @Nullable Intent data
        ) {
            final MutableLiveData<PaymentResult> liveData = new MutableLiveData<>();
            final Stripe stripe = getStripe();
            if (stripe != null) {
                stripe.onPaymentResult(requestCode, data,
                        new ApiResultCallback<PaymentIntentResult>() {
                            @Override
                            public void onSuccess(PaymentIntentResult paymentIntentResult) {
                                liveData.setValue(
                                        new PaymentResult.Success(paymentIntentResult)
                                );
                            }

                            @Override
                            public void onError(@NotNull Exception e) {
                                liveData.setValue(
                                        new PaymentResult.Error(e)
                                );
                            }
                        });
            }
            return liveData;
        }

        abstract static class FetchResult {
            static class Success extends FetchResult {
                @NonNull final JSONObject data;

                Success(@NonNull JSONObject data) {
                    this.data = data;
                }
            }

            static class Error extends FetchResult {
                @NonNull final Exception exception;

                Error(@NonNull Exception exception) {
                    this.exception = exception;
                }
            }
        }

        abstract static class PaymentResult {
            static class Success extends PaymentResult {
                @NonNull final PaymentIntentResult result;

                Success(@NonNull PaymentIntentResult result) {
                    this.result = result;
                }
            }

            static class Error extends PaymentResult {
                @NonNull final Exception exception;

                Error(@NonNull Exception exception) {
                    this.exception = exception;
                }
            }
        }

        private static final String BACKEND_URL = "http://10.0.2.2:4242";
        private static final MediaType MEDIA_TYPE =
                MediaType.get("application/json; charset=utf-8");
    }
}
