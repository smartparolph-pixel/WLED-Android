package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.cgagnier.wlednativeandroid.R

/** All lockup drawables are 727 × 350. */
private const val LOCKUP_ASPECT_RATIO = 727f / 350f
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * The Sinag lockup, following the app's current theme: the color version on light backgrounds and the
 * reversed one on dark backgrounds, or with [mono] the black and white versions (for muted states such
 * as "device unreachable"). Size it with a height or a width; the other side follows.
 */
@Composable
fun SinagLockup(modifier: Modifier = Modifier, mono: Boolean = false) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    val drawable = when {
        mono && dark -> R.drawable.sinag_lockup_mono_white
        mono -> R.drawable.sinag_lockup_mono_black
        dark -> R.drawable.sinag_lockup_reversed
        else -> R.drawable.sinag_lockup_color
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = stringResource(R.string.sinag_logo),
        modifier = modifier.aspectRatio(LOCKUP_ASPECT_RATIO),
    )
}
