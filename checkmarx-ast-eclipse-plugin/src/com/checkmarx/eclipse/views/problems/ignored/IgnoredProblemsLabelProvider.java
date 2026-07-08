package com.checkmarx.eclipse.views.problems.ignored;

import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;

import com.checkmarx.eclipse.views.problems.model.ScanProblem;
import com.checkmarx.eclipse.views.problems.icon.IconRegistry;

/**
 * Label provider for ignored problems tree view. Renders severity icons and
 * formatted text with strikethrough styling to indicate ignored status.
 */
public class IgnoredProblemsLabelProvider extends DelegatingStyledCellLabelProvider {

	public IgnoredProblemsLabelProvider() {
		super(new IStyledLabelProvider() {
			@Override
			public StyledString getStyledText(Object element) {
				if (element instanceof ScanProblem) {
					ScanProblem problem = (ScanProblem) element;
					String text = "[" + problem.getSeverity().name() + "] " + problem.getMessage() +
							" (Line " + problem.getLine() + ")";
					StyledString styledText = new StyledString(text);
					// Strikethrough style for ignored problems
					Styler strikethrough = new Styler() {
						@Override
						public void applyStyles(TextStyle textStyle) {
							textStyle.strikeout = true;
						}
					};
					styledText.setStyle(0, text.length(), strikethrough);
					return styledText;
				} else if (element instanceof String) {
					return new StyledString((String) element, StyledString.QUALIFIER_STYLER);
				}
				return new StyledString("");
			}

			@Override
			public Image getImage(Object element) {
				if (element instanceof ScanProblem) {
					ScanProblem problem = (ScanProblem) element;
					if (problem.getSeverity() != null) {
						try {
							return IconRegistry.getInstance().getIcon(problem.getSeverity().name(), 16);
						} catch (Exception e) {
							return null;
						}
					}
				}
				return null;
			}

			@Override
			public void addListener(ILabelProviderListener listener) {}

			@Override
			public void removeListener(ILabelProviderListener listener) {}

			@Override
			public void dispose() {}

			@Override
			public boolean isLabelProperty(Object element, String property) {
				return false;
			}
		});
	}
}
