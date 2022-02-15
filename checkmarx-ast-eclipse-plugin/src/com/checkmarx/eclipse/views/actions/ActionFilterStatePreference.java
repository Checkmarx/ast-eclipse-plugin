package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolItem;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.google.common.eventbus.EventBus;

class ActionFilterStatePreference extends Action implements IMenuCreator
{
  private Menu menu;
  private static final String ACTION_FILTER_STATE_TOOLTIP = "State";
  private static final String ACTION_FILTER_STATE_ICON_PATH = "/icons/filter_ps.png";
  private EventBus pluginEventBus;
  
  
  public static final String FILTER_NOT_EXPLOITABLE = "Not Exploitable";
  public static final String FILTER_CONFIRMED = "Confirmed";
  public static final String FILTER_PROPOSED_NON_EXPLOITABLE = "Proposed Not Exploitable";
  public static final String FILTER_URGENT = "Urgent";
  public static final String FILTER_IGNORED = "Ignored";
  public static final String FILTER_NOT_IGNORED = "Not Ignored";
  public static final String FILTER_TO_VERIFY = "To Verify";

  ActionFilterStatePreference(EventBus pluginEventBus)
  {
    super(ACTION_FILTER_STATE_TOOLTIP);
    setId(ActionName.FILTER_CHANGED.name());
    setImageDescriptor(Activator.getImageDescriptor(ACTION_FILTER_STATE_ICON_PATH));
    this.pluginEventBus = pluginEventBus;
    setMenuCreator(this);
  }


  @Override
  public void dispose()
  {
    if (menu != null) {
      menu.dispose();
      menu = null;
    }
  }


  @Override
  public void runWithEvent(final Event event)
  {
    if (event.widget instanceof ToolItem)
      {
        final ToolItem toolItem = (ToolItem)event.widget;
        final Control control = toolItem.getParent();
        final Menu menu = getMenu(control);

        final Rectangle bounds = toolItem.getBounds();
        final Point topLeft = new Point(bounds.x, bounds.y + bounds.height);
        menu.setLocation(control.toDisplay(topLeft));
        menu.setVisible(true);
      }
  }

  @Override
  public Menu getMenu(final Control parent)
  {
    if (menu != null) {
      menu.dispose();
    }

    menu = new Menu(parent);
    MenuItem item = new MenuItem(menu, SWT.CHECK);
    item.setText(FILTER_NOT_EXPLOITABLE);
    item.setSelection(FilterState.notExploitable);
    item.addSelectionListener(StateFilterSectionListener(State.NOT_EXPLOITABLE));
    
    MenuItem item2 = new MenuItem(menu, SWT.CHECK);
    item2.setText(FILTER_CONFIRMED);
    item2.setSelection(FilterState.confirmed);
    item2.addSelectionListener(StateFilterSectionListener(State.CONFIRMED));
    
    MenuItem item3 = new MenuItem(menu, SWT.CHECK);
    item3.setText(FILTER_PROPOSED_NON_EXPLOITABLE);
    item3.setSelection(FilterState.proposedNotExploitable);
    item3.addSelectionListener(StateFilterSectionListener(State.PROPOSED_NOT_EXPLOITABLE));
    
    MenuItem item4 = new MenuItem(menu, SWT.CHECK);
    item4.setText(FILTER_IGNORED);
    item4.setSelection(FilterState.ignored);
    item4.addSelectionListener(StateFilterSectionListener(State.IGNORED));
    
    MenuItem item5 = new MenuItem(menu, SWT.CHECK);
    item5.setText(FILTER_TO_VERIFY);
    item5.setSelection(FilterState.to_verify);
    item5.addSelectionListener(StateFilterSectionListener(State.TO_VERIFY));
    
    MenuItem item6 = new MenuItem(menu, SWT.CHECK);
    item6.setText(FILTER_NOT_IGNORED);
    item6.setSelection(FilterState.not_ignored);
    item6.addSelectionListener(StateFilterSectionListener(State.NOT_IGNORED));
    
    MenuItem item7 = new MenuItem(menu, SWT.CHECK);
    item7.setText(FILTER_URGENT);
    item7.setSelection(FilterState.urgent);
    item7.addSelectionListener(StateFilterSectionListener(State.URGENT));
    

    return menu;
  }   

  private SelectionListener StateFilterSectionListener(State state) {
	  return new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				FilterState.setFilterState(state);
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, DataProvider.getInstance().sortResults()));
			}
	    	
	    };
}


@Override
  public Menu getMenu(final Menu parent)
  {
    return null;
  }
}
