package br.com.exameperto.identity;

import java.util.List;

record GrantPageView(List<GrantView> items, String nextCursor) {}
