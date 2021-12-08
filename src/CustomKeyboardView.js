import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {
  InteractionManager, Platform, requireNativeComponent
} from 'react-native'
import TextInputKeyboardManagerIOS from './TextInputKeyboardMangerIOS';
import TextInputKeyboardManagerAndroid from './TextInputKeyboardManagerAndroid';
import KeyboardRegistry from './KeyboardsRegistry';

const IsAndroid = Platform.OS === 'android';
const IsIOS = Platform.OS === 'ios';

const CustomKeyboardViewNativeAndroid = requireNativeComponent('CustomKeyboardViewNative');

export default class CustomKeyboardView extends Component {
  static propTypes = {
    inputRef: PropTypes.object,
    initialProps: PropTypes.object,
    component: PropTypes.string,
    onItemSelected: PropTypes.func,
  };
  static defaultProps = {
    initialProps: {},
  };

  constructor(props) {
    super(props);

    const {inputRef, component, initialProps, onItemSelected, useSafeArea} = props;
    if (component) {
      this.addOnItemSelectListener(onItemSelected, component);

      if (TextInputKeyboardManagerIOS && inputRef) {
        TextInputKeyboardManagerIOS.setInputComponent(inputRef, {component, initialProps});
      }

      this.registeredRequestShowKeyboard = false;
    }

    if( IsAndroid && TextInputKeyboardManagerAndroid ){
      TextInputKeyboardManagerAndroid.setUseSafeArea(useSafeArea)
    }

    this.keyboardExpandedToggle = {};
    if (IsIOS && TextInputKeyboardManagerIOS) {
      KeyboardRegistry.addListener('onToggleExpandedKeyboard', (args) => {
        if (this.props.inputRef) {
          if (this.keyboardExpandedToggle[args.keyboardId] === undefined) {
            this.keyboardExpandedToggle[args.keyboardId] = false;
          }
          this.keyboardExpandedToggle[args.keyboardId] = !this.keyboardExpandedToggle[args.keyboardId];
          TextInputKeyboardManagerIOS.toggleExpandKeyboard(
            this.props.inputRef, this.keyboardExpandedToggle[args.keyboardId], this.props.initialProps.expandWithLayoutAnimation,
          );
        }
      });
    }
  }

  shouldComponentUpdate(nextProps) {
    return (nextProps.component !== this.props.component);
  }

  componentWillUnmount() {
    KeyboardRegistry.removeListeners('onRequestShowKeyboard');
    KeyboardRegistry.removeListeners('onToggleExpandedKeyboard');

    if (this.keyboardEventListeners) {
      this.keyboardEventListeners.forEach(eventListener => eventListener.remove());
    }
    if (this.props.component) {
      KeyboardRegistry.removeListeners(`${this.props.component}.onItemSelected`);
    }
  }

  addOnItemSelectListener(onItemSelected, component) {
    if (onItemSelected) {
      KeyboardRegistry.addListener(`${component}.onItemSelected`, (args) => {
        onItemSelected(component, args);
      });
    }
  }

  async UNSAFE_componentWillReceiveProps(nextProps) { //eslint-disable-line
    const {inputRef, component, initialProps, onRequestShowKeyboard, useSafeArea} = nextProps;

    if (IsAndroid) {
      if (this.props.component !== component && !component) {
        await TextInputKeyboardManagerAndroid.reset();
      }

      if(TextInputKeyboardManagerAndroid && this.props.useSafeArea !== useSafeArea ){
        TextInputKeyboardManagerAndroid.setUseSafeArea(useSafeArea)
      }
    }

    if (IsIOS && TextInputKeyboardManagerIOS && inputRef) {
      if (component && initialProps.reset) {
        TextInputKeyboardManagerIOS.setInputComponent(inputRef, {component, initialProps});
      } else if (component !== this.props.component) {
        TextInputKeyboardManagerIOS.removeInputComponent(inputRef);
      }
    }

    if (onRequestShowKeyboard && !this.registeredRequestShowKeyboard) {
      this.registeredRequestShowKeyboard = true;
      KeyboardRegistry.addListener('onRequestShowKeyboard', (args) => {
        onRequestShowKeyboard(args.keyboardId);
      });
    }
    this.registerListener(this.props, nextProps);
  }

  registerListener(props, nextProps) {
    const {component, onItemSelected} = nextProps;
    if (component && props.component !== component) {
      if (props.component) {
        KeyboardRegistry.removeListeners(`${props.component}.onItemSelected`);
      }
      KeyboardRegistry.removeListeners(`${component}.onItemSelected`);
      this.addOnItemSelectListener(onItemSelected, component);
    }
  }

  render() {
    if (IsAndroid) {
      const {component, initialProps} = this.props;

      if (!component) {
        return null;
      }

      const KeyboardComponent = component && KeyboardRegistry.getKeyboard(component);

      return (
        <CustomKeyboardViewNativeAndroid>
          {KeyboardComponent && <KeyboardComponent {...initialProps}/>}
        </CustomKeyboardViewNativeAndroid>
      );
    }
    return null;
  }
}
