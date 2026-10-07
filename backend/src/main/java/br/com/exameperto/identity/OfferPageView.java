package br.com.exameperto.identity;

import java.util.List;

record OfferPageView(List<OfferView> items, String nextCursor) {}
