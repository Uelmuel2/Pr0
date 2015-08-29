package com.pr0gramm.app.api.categories;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.pr0gramm.app.api.pr0gramm.ApiGsonBuilder;
import com.squareup.okhttp.OkHttpClient;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;

/**
 */
public class ExtraCategoryApiProvider implements Provider<ExtraCategoryApi> {
    private final ExtraCategoryApi api;

    @Inject
    public ExtraCategoryApiProvider(OkHttpClient httpClient) {
        this.api = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl("http://pr0.wibbly-wobbly.de/api/categories/v1/")
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(ApiGsonBuilder.builder().create()))
                .build()
                .create(ExtraCategoryApi.class);
    }

    @Override
    public ExtraCategoryApi get() {
        return api;
    }
}