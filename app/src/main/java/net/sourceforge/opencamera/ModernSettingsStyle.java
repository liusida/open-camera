package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

/** Modern GUI: lighter settings screens - 24dp icons (instead of 48dp), smaller text and
 *  tighter rows. Only applied when the Modern GUI setting is on; the original look is unchanged.
 */
class ModernSettingsStyle {
    private static final int ICON_DP = 28; // the 48dp pngs have ~4dp padding built in, so glyphs read as ~22dp
    private static final float TITLE_SP = 15.0f;
    private static final float SUMMARY_SP = 12.5f;
    private static final float CATEGORY_SP = 12.0f;
    private static final int ROW_PADDING_DP = 9;

    static boolean isEnabled(android.content.Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(PreferenceKeys.ModernGUIPreferenceKey, false);
    }

    /** Shrinks the icons of all preferences under the given group. */
    static void shrinkIcons(PreferenceGroup group, float density) {
        final int size = (int) (ICON_DP * density + 0.5f);
        for(int i=0;i<group.getPreferenceCount();i++) {
            Preference pref = group.getPreference(i);
            Drawable icon = pref.getIcon();
            if( icon != null && icon.getIntrinsicWidth() != size ) {
                // a LayerDrawable with a fixed layer size reports that size as its intrinsic size
                LayerDrawable small = new LayerDrawable(new Drawable[]{icon});
                small.setLayerSize(0, size, size);
                pref.setIcon(small);
            }
            if( pref instanceof PreferenceGroup )
                shrinkIcons((PreferenceGroup)pref, density);
        }
    }

    /** Restyles the visible rows whenever the list lays out (rows are recycled, so this is idempotent). */
    static void styleList(final ListView list) {
        if( list == null )
            return;
        final float density = list.getResources().getDisplayMetrics().density;
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x14FFFFFF));
        list.setDividerHeight(Math.max(1, (int) (density + 0.5f)));
        final Runnable restyle = () -> {
            for(int i=0;i<list.getChildCount();i++)
                styleRow(list.getChildAt(i), density);
        };
        list.getViewTreeObserver().addOnGlobalLayoutListener(restyle::run);
        // rows that scroll into view are not always laid out via a global layout pass
        list.getViewTreeObserver().addOnPreDrawListener(() -> {
            restyle.run();
            return true;
        });
    }

    private static void styleRow(View row, float density) {
        if( Boolean.TRUE.equals(row.getTag(R.id.layout)) )
            return;
        row.setTag(R.id.layout, Boolean.TRUE);
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        if( title != null ) {
            boolean category = summary == null && row.findViewById(android.R.id.icon) == null && title.getParent() == row;
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, category ? CATEGORY_SP : TITLE_SP);
        }
        View icon = row.findViewById(android.R.id.icon);
        if( icon != null && icon.getParent() instanceof View ) {
            // narrow the icon column (the platform reserves ~56dp+) so titles sit closer
            View frame = (View)icon.getParent();
            if( frame != row ) {
                frame.setMinimumWidth(0);
                frame.setPadding(0, 0, (int) (14 * density + 0.5f), 0);
            }
            else if( icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams ) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)icon.getLayoutParams();
                lp.rightMargin = (int) (14 * density + 0.5f);
                icon.setLayoutParams(lp);
            }
            icon.setMinimumWidth(0);
        }
        if( summary != null ) {
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUMMARY_SP);
            summary.setTextColor(0xFFA8A8A8);
        }
        if( title != null || summary != null ) {
            int pad = (int) (ROW_PADDING_DP * density + 0.5f);
            row.setMinimumHeight((int) (48 * density + 0.5f));
            row.setPadding(row.getPaddingLeft(), pad, row.getPaddingRight(), pad);
            // the platform layout adds extra vertical padding/min-height inside the row
            if( row instanceof ViewGroup && ((ViewGroup)row).getChildCount() > 0 ) {
                for(int i=0;i<((ViewGroup)row).getChildCount();i++) {
                    View child = ((ViewGroup)row).getChildAt(i);
                    child.setMinimumHeight(0);
                    if( child instanceof ViewGroup && child.findViewById(android.R.id.title) != null )
                        child.setPadding(child.getPaddingLeft(), 0, child.getPaddingRight(), 0);
                }
            }
        }
    }
}
