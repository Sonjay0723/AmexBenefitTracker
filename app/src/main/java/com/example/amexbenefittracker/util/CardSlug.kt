package com.example.amexbenefittracker.util

/**
 * Stable, human-readable key derived from a display name (card name, benefit
 * name, ...). This is the shared bridge between Room's free-text `name`
 * columns and the fixed keys used in both the Firestore `claims` map and the
 * Cloudflare worker's `card_mappings` (e.g. "The Platinum Card®" ->
 * "the_platinum_card"). All three clients (Android, web, worker) must agree
 * on this derivation, so it lives in exactly one place per client rather
 * than being reimplemented at each call site.
 */
fun String.toSlug(): String = lowercase()
    .replace("®", "")
    .replace("'", "")
    .replace("+", "plus")
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
