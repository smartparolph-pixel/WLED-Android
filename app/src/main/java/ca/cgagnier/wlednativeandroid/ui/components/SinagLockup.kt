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

/** Both lockup drawables are 727 × 350. */
private const val LOCKUP_ASPECT_RATIO = 727f / 350f
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * The Sinag lockup: the color version on light backgrounds, the reversed one on dark backgrounds,
 * following the app's current theme. Size it with a height; the width follows.
 */
@Composable
fun SinagLockup(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    Image(
        painter = painterResource(if (dark) R.drawable.sinag_lockup_reversed else R.drawable.sinag_lockup_color),
        contentDescription = stringResource(R.string.sinag_logo),
        modifier = modifier.aspectRatio(LOCKUP_ASPECT_RATIO),
    )
}
